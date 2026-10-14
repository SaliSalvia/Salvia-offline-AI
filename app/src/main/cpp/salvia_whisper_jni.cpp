// Salvia Offline AI — JNI bridge to whisper.cpp (ggml-org/whisper.cpp v1.9.5).
//
// Shares libsalvia_llama.so with the llama.cpp bridge and links the SAME ggml
// (whisper.cpp reuses the parent-provided ggml target). One model at a time,
// cancelled through an atomic flag, audio in as 16 kHz mono float32.

#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include <unistd.h>

#include "whisper.h"

#define LOG_TAG "SalviaWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex             g_mutex;
whisper_context *      g_ctx = nullptr;
std::atomic<bool>      g_cancel{false};
std::string            g_last_transcript;
std::string            g_last_language;

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

bool abort_callback_fn(void * /*user_data*/) {
  return g_cancel.load(std::memory_order_relaxed);
}

whisper_context * load_from_owned_fd(int fd) {
  char proc_path[64];
  snprintf(proc_path, sizeof(proc_path), "/proc/self/fd/%d", static_cast<int>(fd));

  whisper_context_params cparams = whisper_context_default_params();
  cparams.use_gpu = false; // CPU-only build for Mali safety
  cparams.flash_attn = true;

  whisper_context * ctx = whisper_init_from_file_with_params(proc_path, cparams);
  if (ctx != nullptr) {
    close(static_cast<int>(fd));
    return ctx;
  }

  // Fallback for FDs that cannot be re-opened: read into memory once.
  FILE * f = fdopen(static_cast<int>(fd), "rb");
  if (f == nullptr) {
    close(static_cast<int>(fd));
    return nullptr;
  }
  std::vector<unsigned char> buffer;
  unsigned char chunk[1 << 20];
  size_t n;
  while ((n = fread(chunk, 1, sizeof(chunk), f)) > 0) {
    buffer.insert(buffer.end(), chunk, chunk + n);
  }
  fclose(f); // consumes fd
  if (buffer.empty()) {
    return nullptr;
  }
  return whisper_init_from_buffer_with_params(buffer.data(), buffer.size(), cparams);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperLoadFromFd(JNIEnv * env, jobject, jint fd) {
  std::lock_guard<std::mutex> lock(g_mutex);
  if (g_ctx != nullptr) {
    whisper_free(g_ctx);
    g_ctx = nullptr;
  }
  g_ctx = load_from_owned_fd(static_cast<int>(fd));
  if (g_ctx == nullptr) {
    return to_jstring_utf8(env, "whisper.cpp could not load this model file");
  }
  LOGI("whisper model loaded");
  return nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperLoadFromPath(JNIEnv * env, jobject,
                                                                  jstring jpath) {
  std::lock_guard<std::mutex> lock(g_mutex);
  if (g_ctx != nullptr) {
    whisper_free(g_ctx);
    g_ctx = nullptr;
  }
  const std::string path = jstring_to_std(env, jpath);
  whisper_context_params cparams = whisper_context_default_params();
  cparams.use_gpu = false;
  cparams.flash_attn = true;
  g_ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
  if (g_ctx == nullptr) {
    return to_jstring_utf8(env, "whisper.cpp could not load this model file");
  }
  LOGI("whisper model loaded from path");
  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperFree(JNIEnv *, jobject) {
  std::lock_guard<std::mutex> lock(g_mutex);
  if (g_ctx != nullptr) {
    whisper_free(g_ctx);
    g_ctx = nullptr;
  }
  g_last_transcript.clear();
  g_last_language.clear();
}

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperTranscribe(
    JNIEnv * env, jobject, jfloatArray jsamples, jint n_samples,
    jint n_threads, jstring jlanguage, jboolean translate) {
  std::lock_guard<std::mutex> lock(g_mutex);
  if (g_ctx == nullptr) {
    return to_jstring_utf8(env, "no whisper model is loaded");
  }
  if (jsamples == nullptr || n_samples <= 0) {
    return to_jstring_utf8(env, "empty audio");
  }

  std::vector<float> samples(static_cast<size_t>(n_samples));
  env->GetFloatArrayRegion(jsamples, 0, n_samples, samples.data());
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    return to_jstring_utf8(env, "could not read audio samples");
  }

  g_cancel.store(false, std::memory_order_relaxed);

  whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  params.n_threads = n_threads > 0 ? n_threads : 4;
  params.translate = translate == JNI_TRUE;
  params.print_progress = false;
  params.print_realtime = false;
  params.print_timestamps = false;
  params.print_special = false;
  params.detect_language = jlanguage == nullptr;
  params.abort_callback = abort_callback_fn;
  params.abort_callback_user_data = nullptr;

  std::string language = jstring_to_std(env, jlanguage);
  params.language = language.empty() ? nullptr : language.c_str();

  const int rc = whisper_full(g_ctx, params, samples.data(), n_samples);
  if (rc != 0) {
    return to_jstring_utf8(env, g_cancel.load() ? "transcription cancelled" : "whisper_full failed");
  }

  std::string out;
  const int n_segments = whisper_full_n_segments(g_ctx);
  for (int i = 0; i < n_segments; i++) {
    const char * text = whisper_full_get_segment_text(g_ctx, i);
    if (text != nullptr) {
      out += text;
    }
  }

  g_last_transcript = out;
  const int lang_id = whisper_full_lang_id(g_ctx);
  g_last_language = lang_id >= 0 ? whisper_lang_str(lang_id) : (language.empty() ? "auto" : language);
  return nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperTranscript(JNIEnv * env, jobject) {
  std::lock_guard<std::mutex> lock(g_mutex);
  return to_jstring_utf8(env, g_last_transcript);
}

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperLastLanguage(JNIEnv * env, jobject) {
  std::lock_guard<std::mutex> lock(g_mutex);
  return to_jstring_utf8(env, g_last_language);
}

JNIEXPORT jstring JNICALL
Java_com_example_core_asr_NativeWhisper_nativeWhisperModelLabel(JNIEnv * env, jobject) {
  std::lock_guard<std::mutex> lock(g_mutex);
  return to_jstring_utf8(env, g_ctx != nullptr ? "whisper.cpp" : "");
}

} // extern "C"
