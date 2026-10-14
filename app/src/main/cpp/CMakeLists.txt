# Salvia Offline AI — native inference layer (llama.cpp JNI bridge)
#
# The llama.cpp sources are vendored at build time into ./llama.cpp and are NOT
# committed to git (see .github/workflows/build-android-apk.yml and .gitignore).
cmake_minimum_required(VERSION 3.22)
project(salvia_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
  message(FATAL_ERROR
    "llama.cpp sources are missing at ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp. "
    "Fetch them first, e.g.: "
    "curl -sSL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/v0.6.0 | tar xz --strip-components=1 -C app/src/main/cpp/llama.cpp")
endif()
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/CMakeLists.txt")
  message(FATAL_ERROR
    "whisper.cpp sources are missing at ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp. "
    "Fetch them first, e.g.: "
    "curl -sSL https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.9.5 | tar xz --strip-components=1 -C app/src/main/cpp/whisper.cpp")
endif()

# Build only the llama/ggml libraries — no tests, tools, examples or server.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
# whisper.cpp: reuse the ggml targets created by llama.cpp above (no second copy).
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV   OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL           OFF CACHE BOOL "" FORCE)
# Cross-compiling for Android: never emit host-CPU tuned code.
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
# The NDK OpenMP runtime is fragile; ggml's own thread pool drives the GEMM paths.
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# Static llama/ggml linked into one shared JNI library: a single .so in the APK.
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp-build)
# whisper.cpp detects the parent-provided `ggml` target and links it directly.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp-build)

add_library(salvia_llama SHARED salvia_llama_jni.cpp salvia_whisper_jni.cpp)

target_compile_options(salvia_llama PRIVATE -fvisibility=hidden -O2)
# Never export symbols from the static third-party archives: both llama.cpp and
# whisper.cpp embed ggml ABI details that must not interpose each other.
target_link_options(salvia_llama PRIVATE "-Wl,--exclude-libs,ALL")

find_library(log-lib log)
target_link_libraries(salvia_llama PRIVATE llama whisper ${log-lib})
