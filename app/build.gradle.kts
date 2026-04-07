plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
    id("com.google.gms.google-services")
    alias(libs.plugins.baselineprofile)
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

    signingConfigs {
        create("release") {
            if (!providers.environmentVariable("KEYSTORE_PASSWORD").isPresent) {
                return@create
            }
            keyAlias = "release"
            keyPassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
            storeFile = file(project.projectDir.absolutePath + "/keystore.jks")
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "PUSH_SERVER_URL", "\"http://10.0.2.2:9999/subscribe\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (providers.environmentVariable("KEYSTORE_PASSWORD").isPresent)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            buildConfigField("String", "PUSH_SERVER_URL", "\"https://matrice.wallera.computer/subscribe\"")
        }
        create("profile") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "PUSH_SERVER_URL", "\"http://10.0.2.2:9999/subscribe\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        version = JavaVersion.VERSION_21.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.splashscreen)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ozone.bluesky)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.placeholder.material3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.paging.compose)
    implementation(libs.telephoto.zoomable)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.datetime)
    implementation(libs.human.readable)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.compose.animation.core.lint)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
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
