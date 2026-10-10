// Salvia Offline AI — JNI bridge to llama.cpp (ggml-org/llama.cpp v0.6.0).
//
// Design notes:
//  * One model/context at a time (single product policy), guarded by a mutex.
//  * Models are opened through a caller-owned file descriptor so multi-GB GGUF
//    files are never copied into app storage: the /proc/self/fd/N path keeps
//    mmap support, with an fdopen fallback that reads through FILE*.
//  * Tokens stream to Java as complete UTF-8 text only (token pieces can split
//    code points; we buffer until a full character is available).
//  * Cancellation is a lock-free flag checked between decode steps and by the
//    context's abort callback, so a long llama_decode() can be interrupted.

#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include <unistd.h>

#include "llama.h"

#define LOG_TAG "SalviaLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex              g_model_mutex;   // load/unload
std::mutex              g_generate_mutex; // serialize generation runs
llama_model *           g_model = nullptr;
llama_context *         g_ctx = nullptr;
uint32_t                g_ctx_size = 0;
std::atomic<bool>       g_cancel{false};
std::atomic<bool>       g_backend_ready{false};
int                     g_last_status = 0; // 0 = completed, 1 = cancelled

// Last-run statistics: [promptTokens, promptMs, genTokens, genMs, tokensPerSec]
float                   g_stats[5] = {0, 0, 0, 0, 0};

struct Callbacks {
  JNIEnv *    env = nullptr;
  jobject     obj = nullptr;       // global ref for the duration of one call
  jmethodID   on_token = nullptr;  // void onToken(String)
  jmethodID   on_progress = nullptr; // void onLoadProgress(float)
};

// ---------------------------------------------------------------------------
// UTF-8 helpers
// ---------------------------------------------------------------------------

// Length of the longest prefix of `s` that ends on a complete UTF-8 sequence.
size_t utf8_complete_prefix(const std::string & s) {
  const size_t n = s.size();
  if (n == 0) {
    return 0;
  }
  // Walk back over at most 3 continuation bytes to find a sequence start.
  size_t back = 0;
  while (back < 3 && back < n && (static_cast<unsigned char>(s[n - 1 - back]) & 0xC0) == 0x80) {
    back++;
  }
  if (back == 0) {
    // Last byte is ASCII (or invalid lead) — everything is complete.
    return n;
  }
  const unsigned char lead = static_cast<unsigned char>(s[n - 1 - back]);
  int expected = 0;
  if ((lead & 0xE0) == 0xC0) {
    expected = 2;
  } else if ((lead & 0xF0) == 0xE0) {
    expected = 3;
  } else if ((lead & 0xF8) == 0xF0) {
    expected = 4;
  } else {
    // Malformed lead byte — emit what we have rather than stalling.
    return n;
  }
  const int have = static_cast<int>(back) + 1;
  return have < expected ? n - have : n;
}

// Create a proper jstring from UTF-8 bytes (NewStringUTF mangles non-ASCII).
jstring to_jstring_utf8(JNIEnv * env, const std::string & s) {
  jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
  if (bytes == nullptr) {
    return nullptr;
  }
  env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()),
                          reinterpret_cast<const jbyte *>(s.data()));
  jclass str_cls = env->FindClass("java/lang/String");
  jmethodID ctor = env->GetMethodID(str_cls, "<init>", "([BLjava/lang/String;)V");
  jstring charset = env->NewStringUTF("UTF-8");
  jstring result = static_cast<jstring>(env->NewObject(str_cls, ctor, bytes, charset));
  env->DeleteLocalRef(bytes);
  env->DeleteLocalRef(charset);
  env->DeleteLocalRef(str_cls);
  return result;
}

void throw_runtime(JNIEnv * env, const std::string & msg) {
  LOGE("%s", msg.c_str());
  jclass cls = env->FindClass("java/lang/RuntimeException");
  if (cls != nullptr) {
    env->ThrowNew(cls, msg.c_str());
    env->DeleteLocalRef(cls);
  }
}

std::string jstring_to_std(JNIEnv * env, jstring js) {
  if (js == nullptr) {
    return std::string();
  }
  const char * chars = env->GetStringUTFChars(js, nullptr);
  std::string out(chars != nullptr ? chars : "");
  if (chars != nullptr) {
    env->ReleaseStringUTFChars(js, chars);
  }
  return out;
}

// ---------------------------------------------------------------------------
// llama.cpp callbacks
// ---------------------------------------------------------------------------

bool abort_callback_fn(void * /*user_data*/) {
  return g_cancel.load(std::memory_order_relaxed);
}

bool progress_callback_fn(float progress, void * user_data) {
  auto * cb = static_cast<Callbacks *>(user_data);
  if (cb != nullptr && cb->env != nullptr && cb->obj != nullptr && cb->on_progress != nullptr) {
    cb->env->CallVoidMethod(cb->obj, cb->on_progress, static_cast<jfloat>(progress * 100.0f));
    if (cb->env->ExceptionCheck()) {
      cb->env->ExceptionClear();
    }
  }
  // Returning false aborts the load; the Java cancel flag is not used here.
  return true;
}

// ---------------------------------------------------------------------------
// Generation core
// ---------------------------------------------------------------------------

std::string apply_chat_template(const llama_model * model,
                                const std::vector<std::string> & roles,
                                const std::vector<std::string> & contents) {
  const size_t n = roles.size();
  std::vector<llama_chat_message> msgs(n);
  for (size_t i = 0; i < n; i++) {
    msgs[i].role = roles[i].c_str();
    msgs[i].content = contents[i].c_str();
  }

  const char * tmpl = llama_model_chat_template(model, nullptr);
  int32_t needed = llama_chat_apply_template(tmpl, msgs.data(), n, true, nullptr, 0);
  if (needed > 0) {
    std::string out(static_cast<size_t>(needed) + 1, '\0');
    int32_t written = llama_chat_apply_template(tmpl, msgs.data(), n, true, &out[0],
                                                static_cast<int32_t>(out.size()));
    if (written >= 0) {
      out.resize(static_cast<size_t>(written < needed ? written : needed));
      return out;
    }
  }

  // Fallback formatting when the model has no usable built-in template.
  std::string out;
  for (size_t i = 0; i < n; i++) {
    out += roles[i];
    out += ": ";
    out += contents[i];
    out += "\n";
  }
  out += "assistant:";
  return out;
}

struct RunStats {
  int32_t prompt_tokens = 0;
  double prompt_ms = 0;
  int32_t gen_tokens = 0;
  double gen_ms = 0;
};

// Returns true when the run finished normally (EOG or max tokens).
bool run_generation(JNIEnv * env,
                    const jobject cb_obj,
                    const jmethodID on_token,
                    const std::string & prompt,
                    int32_t max_tokens,
                    float temperature,
                    float top_p,
                    int32_t top_k,
                    float repeat_penalty,
                    uint32_t n_ctx_req,
                    int32_t n_threads,
                    std::string & out_text,
                    RunStats & stats) {
  using clock = std::chrono::steady_clock;

  const llama_vocab * vocab = llama_model_get_vocab(g_model);

  // --- tokenize prompt -----------------------------------------------------
  int32_t n_prompt = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                    nullptr, 0, true, false);
  if (n_prompt < 0) {
    n_prompt = -n_prompt;
  }
  std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
  int32_t got = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                               prompt_tokens.data(), n_prompt, true, false);
  if (got < 0) {
    throw_runtime(env, "tokenize failed");
    return false;
  }
  prompt_tokens.resize(static_cast<size_t>(got));
  stats.prompt_tokens = got;

  const int32_t n_train = llama_model_n_ctx_train(g_model);
  uint32_t want_ctx = n_ctx_req;
  if (want_ctx == 0) {
    want_ctx = 2048;
  }
  if (n_train > 0 && want_ctx > static_cast<uint32_t>(n_train)) {
    want_ctx = static_cast<uint32_t>(n_train);
  }
  if (prompt_tokens.size() + 8 >= want_ctx) {
    // Keep at least a little generation head-room.
    want_ctx = static_cast<uint32_t>(prompt_tokens.size() + 16);
    if (n_train > 0 && want_ctx > static_cast<uint32_t>(n_train)) {
      throw_runtime(env, "conversation is longer than the model context window");
      return false;
    }
  }

  // --- (re)create context if needed --------------------------------------
  if (g_ctx == nullptr || g_ctx_size != want_ctx) {
    if (g_ctx != nullptr) {
      llama_free(g_ctx);
      g_ctx = nullptr;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = want_ctx;
    cparams.n_batch = 512;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.abort_callback = abort_callback_fn;
    cparams.abort_callback_data = nullptr;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
      throw_runtime(env, "failed to create llama context (out of memory?)");
      return false;
    }
    g_ctx_size = want_ctx;
  } else {
    llama_set_n_threads(g_ctx, n_threads > 0 ? n_threads : 4,
                        n_threads > 0 ? n_threads : 4);
  }

  // Fresh run: drop any KV state left from a previous conversation.
  llama_memory_clear(llama_get_memory(g_ctx), true);

  // --- sampler chain ------------------------------------------------------
  const int32_t n_vocab = llama_vocab_n_tokens(vocab);
  llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
  if (temperature <= 0.05f) {
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
  } else {
    llama_sampler_chain_add(smpl,
        llama_sampler_init_penalties(n_vocab, 64, repeat_penalty, 0.0f, 0.0f));
    if (top_k > 0) {
      llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
  }

  auto finish = [&](bool completed) {
    llama_sampler_free(smpl);
    return completed;
  };

  // --- prompt prefill (chunked) ------------------------------------------
  const uint32_t n_batch = llama_n_batch(g_ctx);
  const auto t0 = clock::now();
  for (size_t i = 0; i < prompt_tokens.size(); i += n_batch) {
    if (g_cancel.load(std::memory_order_relaxed)) {
      stats.prompt_ms = std::chrono::duration<double, std::milli>(clock::now() - t0).count();
      return finish(false);
    }
    const size_t chunk = std::min<size_t>(n_batch, prompt_tokens.size() - i);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i,
                                           static_cast<int32_t>(chunk));
    if (llama_decode(g_ctx, batch) != 0) {
      throw_runtime(env, g_cancel.load() ? "generation cancelled" : "llama_decode failed during prompt evaluation");
      return finish(false);
    }
    // Feed the prompt into the sampler history so repeat penalties see it.
    for (size_t j = 0; j < chunk; j++) {
      llama_sampler_accept(smpl, prompt_tokens[i + j]);
    }
  }
  stats.prompt_ms = std::chrono::duration<double, std::milli>(clock::now() - t0).count();

  // --- token generation loop ---------------------------------------------
  const auto t1 = clock::now();
  std::string pending_utf8;
  int32_t n_gen = 0;

  while (n_gen < max_tokens) {
    if (g_cancel.load(std::memory_order_relaxed)) {
      break;
    }

    // llama_sampler_sample() accepts the token into the chain internally.
    const llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);

    if (llama_vocab_is_eog(vocab, tok)) {
      stats.gen_tokens = n_gen;
      stats.gen_ms = std::chrono::duration<double, std::milli>(clock::now() - t1).count();
      return finish(true);
    }

    char piece_buf[256];
    const int n_piece = llama_token_to_piece(vocab, tok, piece_buf,
                                             static_cast<int32_t>(sizeof(piece_buf)), 0, false);
    if (n_piece > 0) {
      pending_utf8.append(piece_buf, static_cast<size_t>(n_piece));
      out_text.append(piece_buf, static_cast<size_t>(n_piece));
      const size_t complete = utf8_complete_prefix(pending_utf8);
      if (complete > 0 && cb_obj != nullptr && on_token != nullptr) {
        jstring js = to_jstring_utf8(env, pending_utf8.substr(0, complete));
        if (js != nullptr) {
          env->CallVoidMethod(cb_obj, on_token, js);
          env->DeleteLocalRef(js);
          if (env->ExceptionCheck()) {
            // A Java-side failure must not leave a pending exception behind.
            env->ExceptionClear();
          }
        }
        pending_utf8.erase(0, complete);
      }
    }
    n_gen++;

    llama_batch batch = llama_batch_get_one(&tok, 1);
    if (llama_decode(g_ctx, batch) != 0) {
      if (g_cancel.load(std::memory_order_relaxed)) {
        break;
      }
      stats.gen_tokens = n_gen;
      stats.gen_ms = std::chrono::duration<double, std::milli>(clock::now() - t1).count();
      throw_runtime(env, "llama_decode failed during generation");
      return finish(false);
    }
  }

  // Flush any trailing complete bytes (partial code point is dropped on stop).
  if (!pending_utf8.empty() && cb_obj != nullptr && on_token != nullptr) {
    const size_t complete = utf8_complete_prefix(pending_utf8);
    if (complete > 0) {
      jstring js = to_jstring_utf8(env, pending_utf8.substr(0, complete));
      if (js != nullptr) {
        env->CallVoidMethod(cb_obj, on_token, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) {
          env->ExceptionClear();
        }
      }
    }
  }

  stats.gen_tokens = n_gen;
  stats.gen_ms = std::chrono::duration<double, std::milli>(clock::now() - t1).count();
  return finish(!g_cancel.load(std::memory_order_relaxed));
}

} // namespace

// ---------------------------------------------------------------------------
// JNI exports
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_core_llm_NativeLlama_nativeBootstrap(JNIEnv *, jclass) {
  static std::once_flag once;
  std::call_once(once, [] {
    llama_backend_init();
    g_backend_ready.store(true);
    LOGI("llama.cpp backend initialized");
  });
  return g_backend_ready.load() ? JNI_TRUE : JNI_FALSE;
}

// Loads a GGUF model from a caller-owned fd. The fd is consumed (closed) by
// this call regardless of the outcome. Returns null on success or a message.
JNIEXPORT jstring JNICALL
Java_com_example_core_llm_NativeLlama_nativeLoadFromFd(JNIEnv * env, jclass,
                                                       jint fd, jint n_gpu_layers,
                                                       jobject callback) {
  // Lock order everywhere: generate mutex first, then model mutex.
  std::lock_guard<std::mutex> run_lock(g_generate_mutex);
  std::lock_guard<std::mutex> lock(g_model_mutex);

  if (g_model != nullptr) {
    llama_model_free(g_model);
    g_model = nullptr;
    if (g_ctx != nullptr) {
      llama_free(g_ctx);
      g_ctx = nullptr;
      g_ctx_size = 0;
    }
  }

  Callbacks cb;
  cb.env = env;
  cb.obj = callback;
  if (callback != nullptr) {
    jclass cls = env->GetObjectClass(callback);
    cb.on_progress = env->GetMethodID(cls, "onLoadProgress", "(F)V");
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      cb.on_progress = nullptr;
    }
  }

  llama_model_params mparams = llama_model_default_params();
  mparams.n_gpu_layers = n_gpu_layers;
  mparams.load_mode = LLAMA_LOAD_MODE_AUTO;
  mparams.progress_callback = progress_callback_fn;
  mparams.progress_callback_user_data = &cb;

  char proc_path[64];
  snprintf(proc_path, sizeof(proc_path), "/proc/self/fd/%d", static_cast<int>(fd));
  g_model = llama_model_load_from_file(proc_path, mparams);

  if (g_model == nullptr) {
    // Provider FDs that are not re-openable fall back to direct FILE* reads.
    FILE * f = fdopen(static_cast<int>(fd), "rb");
    if (f != nullptr) {
      g_model = llama_model_load_from_file_ptr(f, mparams);
      fclose(f); // consumes fd
    } else {
      close(static_cast<int>(fd));
    }
  } else {
    close(static_cast<int>(fd)); // llama holds its own handle/mapping now
  }

  if (g_model == nullptr) {
    return to_jstring_utf8(env, "llama.cpp could not load this GGUF file");
  }
  LOGI("model loaded (fd path)");
  return nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_example_core_llm_NativeLlama_nativeLoadFromPath(JNIEnv * env, jclass,
                                                         jstring jpath, jint n_gpu_layers,
                                                         jobject callback) {
  std::lock_guard<std::mutex> run_lock(g_generate_mutex);
  std::lock_guard<std::mutex> lock(g_model_mutex);

  if (g_model != nullptr) {
    llama_model_free(g_model);
    g_model = nullptr;
    if (g_ctx != nullptr) {
      llama_free(g_ctx);
      g_ctx = nullptr;
      g_ctx_size = 0;
    }
  }

  const std::string path = jstring_to_std(env, jpath);

  Callbacks cb;
  cb.env = env;
  cb.obj = callback;
  if (callback != nullptr) {
    jclass cls = env->GetObjectClass(callback);
    cb.on_progress = env->GetMethodID(cls, "onLoadProgress", "(F)V");
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      cb.on_progress = nullptr;
    }
  }

  llama_model_params mparams = llama_model_default_params();
  mparams.n_gpu_layers = n_gpu_layers;
  mparams.load_mode = LLAMA_LOAD_MODE_AUTO;
  mparams.progress_callback = progress_callback_fn;
  mparams.progress_callback_user_data = &cb;

  g_model = llama_model_load_from_file(path.c_str(), mparams);
  if (g_model == nullptr) {
    return to_jstring_utf8(env, "llama.cpp could not load this GGUF file");
  }
  LOGI("model loaded from path");
  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_example_core_llm_NativeLlama_nativeUnload(JNIEnv *, jclass) {
  // Wait for any in-flight generation before tearing the model down.
  std::lock_guard<std::mutex> run_lock(g_generate_mutex);
  std::lock_guard<std::mutex> lock(g_model_mutex);
  if (g_ctx != nullptr) {
    llama_free(g_ctx);
    g_ctx = nullptr;
    g_ctx_size = 0;
  }
  if (g_model != nullptr) {
    llama_model_free(g_model);
    g_model = nullptr;
  }
  LOGI("model unloaded");
}

JNIEXPORT void JNICALL
Java_com_example_core_llm_NativeLlama_nativeCancel(JNIEnv *, jclass) {
  g_cancel.store(true, std::memory_order_relaxed);
}

JNIEXPORT jint JNICALL
Java_com_example_core_llm_NativeLlama_nativeLastRunStatus(JNIEnv *, jclass) {
  return g_last_status;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_core_llm_NativeLlama_nativeLastStats(JNIEnv * env, jclass) {
  jfloatArray out = env->NewFloatArray(5);
  if (out != nullptr) {
    env->SetFloatArrayRegion(out, 0, 5, g_stats);
  }
  return out;
}

// Returns [modelName, architecture, ctxTrain, vocabSize, chatTemplate] or null.
JNIEXPORT jobjectArray JNICALL
Java_com_example_core_llm_NativeLlama_nativeModelInfo(JNIEnv * env, jclass) {
  std::lock_guard<std::mutex> lock(g_generate_mutex);
  if (g_model == nullptr) {
    return nullptr;
  }

  auto meta_str = [&](const char * key) -> std::string {
    char buf[512];
    const int n = llama_model_meta_val_str(g_model, key, buf, sizeof(buf));
    return n >= 0 ? std::string(buf) : std::string();
  };

  std::string name = meta_str("general.name");
  if (name.empty()) {
    name = meta_str("general.name.en");
  }
  const std::string arch = meta_str("general.architecture");
  const std::string tmpl_name =
      llama_model_chat_template(g_model, nullptr) != nullptr
          ? llama_model_chat_template(g_model, nullptr)
          : "";

  std::vector<std::string> fields = {
      name,
      arch,
      std::to_string(llama_model_n_ctx_train(g_model)),
      std::to_string(llama_vocab_n_tokens(llama_model_get_vocab(g_model))),
      tmpl_name,
  };

  jclass str_cls = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(static_cast<jsize>(fields.size()), str_cls, nullptr);
  for (size_t i = 0; i < fields.size(); i++) {
    jstring js = to_jstring_utf8(env, fields[i]);
    env->SetObjectArrayElement(arr, static_cast<jsize>(i), js);
    env->DeleteLocalRef(js);
  }
  env->DeleteLocalRef(str_cls);
  return arr;
}

// Runs one full chat turn. Returns the generated text (also streamed through
// callback.onToken) or null when cancelled. Throws RuntimeException on errors.
JNIEXPORT jstring JNICALL
Java_com_example_core_llm_NativeLlama_nativeGenerate(
    JNIEnv * env, jclass,
    jobjectArray jroles, jobjectArray jcontents,
    jint max_tokens, jfloat temperature, jfloat top_p, jint top_k,
    jfloat repeat_penalty, jint n_ctx, jint n_threads,
    jobject callback) {
  std::lock_guard<std::mutex> run_lock(g_generate_mutex);

  g_cancel.store(false, std::memory_order_relaxed);
  g_last_status = 0;
  g_stats[0] = g_stats[1] = g_stats[2] = g_stats[3] = g_stats[4] = 0;

  {
    std::lock_guard<std::mutex> model_lock(g_model_mutex);
    if (g_model == nullptr) {
      throw_runtime(env, "no model is loaded");
      return nullptr;
    }
  }

  const jsize n = env->GetArrayLength(jroles);
  if (n == 0 || env->GetArrayLength(jcontents) != n) {
    throw_runtime(env, "empty or inconsistent chat history");
    return nullptr;
  }

  std::vector<std::string> roles(static_cast<size_t>(n));
  std::vector<std::string> contents(static_cast<size_t>(n));
  for (jsize i = 0; i < n; i++) {
    auto role = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
    auto content = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
    roles[static_cast<size_t>(i)] = jstring_to_std(env, role);
    contents[static_cast<size_t>(i)] = jstring_to_std(env, content);
    env->DeleteLocalRef(role);
    env->DeleteLocalRef(content);
  }

  const std::string prompt = apply_chat_template(g_model, roles, contents);

  Callbacks cb;
  cb.env = env;
  cb.obj = nullptr;
  if (callback != nullptr) {
    cb.obj = env->NewGlobalRef(callback);
    jclass cls = env->GetObjectClass(callback);
    cb.on_token = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      cb.on_token = nullptr;
    }
  }

  std::string out_text;
  RunStats stats;
  bool completed = false;
  try {
    completed = run_generation(env, cb.obj, cb.on_token, prompt,
                               static_cast<int32_t>(max_tokens),
                               static_cast<float>(temperature),
                               static_cast<float>(top_p),
                               static_cast<int32_t>(top_k),
                               static_cast<float>(repeat_penalty),
                               static_cast<uint32_t>(n_ctx),
                               static_cast<int32_t>(n_threads),
                               out_text, stats);
  } catch (...) {
    if (cb.obj != nullptr) {
      env->DeleteGlobalRef(cb.obj);
    }
    throw_runtime(env, "unexpected native failure during generation");
    return nullptr;
  }

  if (cb.obj != nullptr) {
    env->DeleteGlobalRef(cb.obj);
  }

  if (env->ExceptionCheck()) {
    // run_generation already queued a RuntimeException; propagate it.
    return nullptr;
  }

  g_last_status = completed ? 0 : 1;
  g_stats[0] = static_cast<float>(stats.prompt_tokens);
  g_stats[1] = static_cast<float>(stats.prompt_ms);
  g_stats[2] = static_cast<float>(stats.gen_tokens);
  g_stats[3] = static_cast<float>(stats.gen_ms);
  g_stats[4] = stats.gen_ms > 0.0
                   ? static_cast<float>(stats.gen_tokens * 1000.0 / stats.gen_ms)
                   : 0.0f;

  return to_jstring_utf8(env, out_text);
}

} // extern "C"
