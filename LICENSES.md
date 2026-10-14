# Third-Party Licenses

Salvia Offline AI ships with the following third-party components.

## llama.cpp (runtime inference engine)

- **Component:** [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) (pinned release `v0.6.0`, including the in-tree `ggml` library)
- **How it is used:** compiled into `libsalvia_llama.so` (arm64-v8a) and called through a JNI
  bridge (`app/src/main/cpp/salvia_llama_jni.cpp`) to run GGUF language models fully offline.
- **Source availability:** the sources are fetched at build time by the CI workflow
  (`.github/workflows/build-android-apk.yml`) into `app/src/main/cpp/llama.cpp/`, which is
  git-ignored. Any rebuild can reproduce the exact code from the pinned tag.
- **License:** MIT — Copyright (c) 2023-2024 The ggml authors. Full text:
  <https://github.com/ggml-org/llama.cpp/blob/master/LICENSE>

## whisper.cpp (speech-to-text engine)

- **Component:** [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) (pinned release `v1.9.5`)
- **How it is used:** compiled into `libsalvia_llama.so` alongside llama.cpp and called through
  the JNI bridge (`app/src/main/cpp/salvia_whisper_jni.cpp`) to transcribe speech fully offline.
  whisper.cpp reuses the same in-tree `ggml` built for llama.cpp (one ggml copy in the binary).
- **Source availability:** fetched at build time by the CI workflow into
  `app/src/main/cpp/whisper.cpp/` (git-ignored) from the pinned tag.
- **License:** MIT — Copyright (c) 2023-2024 The ggml authors. Full text:
  <https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE>

## Other bundled dependencies

AndroidX, Jetpack Compose, Kotlin, Room and Coil are consumed as binary artifacts from Maven
repositories and are covered by their respective Apache-2.0 / Kotlin Apache-2.0 licenses.
No Firebase, analytics, or network SDKs are included; the app declares no `INTERNET` permission.
