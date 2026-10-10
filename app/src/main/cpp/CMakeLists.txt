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

# Build only the llama/ggml libraries — no tests, tools, examples or server.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
# Cross-compiling for Android: never emit host-CPU tuned code.
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
# The NDK OpenMP runtime is fragile; ggml's own thread pool drives the GEMM paths.
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# Static llama/ggml linked into one shared JNI library: a single .so in the APK.
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp-build)

add_library(salvia_llama SHARED salvia_llama_jni.cpp)

target_compile_options(salvia_llama PRIVATE -fvisibility=hidden -O2)

find_library(log-lib log)
target_link_libraries(salvia_llama PRIVATE llama ${log-lib})
