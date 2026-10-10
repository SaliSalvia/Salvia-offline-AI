plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  // The app does not currently require Android 16 minor-release APIs. Pinning the stable API
  // level keeps local and CI builds reproducible while still targeting Android 16 below.
  compileSdk { version = release(36) }

  defaultConfig {
    applicationId = "com.aistudio.deepgguf.xqmorp"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // The target device (Redmi Note 14 Pro class) is arm64-v8a only; shipping a
    // single ABI keeps the APK small and the native build fast.
    ndk {
      abiFilters.add("arm64-v8a")
    }
    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_STL=c++_static")
        cppFlags += "-std=c++17"
      }
    }
  }

  signingConfigs {
    // Release identity comes from the CI-provided keystore. The keystore itself is never
    // committed; see .github/workflows/build-android-apk.yml.
    val envKeystorePath = System.getenv("KEYSTORE_PATH")
    val envStorePassword = System.getenv("STORE_PASSWORD")
    val envKeyPassword = System.getenv("KEY_PASSWORD")
    if (envKeystorePath != null && envStorePassword != null && envKeyPassword != null) {
      create("release") {
        storeFile = file(envKeystorePath)
        storePassword = envStorePassword
        keyAlias = System.getenv("KEY_ALIAS") ?: "salvia"
        keyPassword = envKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 shrinking/obfuscation with JNI-safe keep rules (app/proguard-rules.pro):
      // smaller APK, faster startup and dex loading on the target device.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    // Use Android Gradle Plugin's standard debug signing key. A custom ignored keystore made CI
    // builds fail because the file was not present in a fresh checkout.
    debug { isDebuggable = true }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  ndkVersion = "27.2.12479018"
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
