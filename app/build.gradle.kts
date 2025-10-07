plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
}

android {
    namespace = "industries.geesawra.monarch"
    compileSdk = 36

    defaultConfig {
        applicationId = "industries.geesawra.monarch"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        version = JavaVersion.VERSION_21.toString()
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("io.ktor:ktor-client-cio:3.0.1") // Or another engine like OkHttp
    implementation("io.ktor:ktor-client-plugins:3.0.1") // Or more specifically:
    implementation("io.ktor:ktor-client-core:3.0.1") // Or the version aligned with the library
    implementation("io.ktor:ktor-client-okhttp:3.0.1") // Or your preferred engine
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("sh.christian.ozone:bluesky:0.3.3")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("io.coil-kt.coil3:coil-compose:3.0.1")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.1")
    implementation("io.github.fornewid:placeholder-material3:2.0.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0") // [Required] androidx.media3 ExoPlayer dependency
    implementation("androidx.media3:media3-session:1.8.0") // [Required] MediaSession Extension dependency
    implementation("androidx.media3:media3-ui:1.8.0") // [Required] Base Player UI
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("com.google.dagger:hilt-android:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.datastore:datastore:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.paging:paging-compose:3.3.0-alpha05")
    implementation("me.saket.telephoto:zoomable:0.17.0")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.17.0")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation(libs.androidx.compose.animation.core.lint)
    implementation(libs.androidx.material3)
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
