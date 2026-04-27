plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.andrewwin.sumup"
    compileSdk = 36

    flavorDimensions += "abi"

    defaultConfig {
        applicationId = "com.andrewwin.sumup"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("universal") {
            dimension = "abi"
        }
        create("phoneArm64") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("phoneArmV7") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // isMinifyEnabled = true
            // isShrinkResources = true

            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation("com.prof18.rssparser:rssparser:6.1.4")
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation(libs.onnxruntime.android)
    implementation(libs.onnxruntime.extensions.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // YouTube Transcripts Support
    implementation("io.github.thoroldvix:youtube-transcript-api:0.4.0")
    // Fix for NoClassDefFoundError: javax.xml.stream.XMLInputFactory on Android
    implementation("com.fasterxml:aalto-xml:1.3.3")
    implementation("stax:stax-api:1.0.1")

    implementation(libs.kotlinx.serialization.json)

    // Readability for smart content extraction
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
