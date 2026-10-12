plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.oflinetok.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oflinetok.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"

        // ONNX Runtime ships native code per CPU type; phones only need the ARM ones.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // CI passes the permanent release key through these env vars (see .github/workflows/build.yml),
    // so every published APK installs as an update over the previous one.
    val keystorePath = System.getenv("SIGNING_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "oflinetok"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to the debug key for local builds without the release keystore.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Compress native libraries inside the APK: ONNX Runtime is ~50 MB raw, far less zipped,
    // which keeps update downloads small. Android unpacks them once at install.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // On-device CLIP model for tag suggestions and smart search.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Real org.json; Android's is a stub in unit tests.
}

tasks.withType<Test>().configureEach {
    systemProperty("clipDir", providers.gradleProperty("clipDir").getOrElse(""))
    systemProperty("cocoDir", providers.gradleProperty("cocoDir").getOrElse(""))
    systemProperty("clipOut", layout.buildDirectory.dir("clip-test").get().asFile.path)
}
