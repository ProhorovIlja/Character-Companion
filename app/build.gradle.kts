plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.caicompanion.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.caicompanion.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 27
        versionName = "0.27.0-rc1"
    }
    flavorDimensions += "browserEngine"
    productFlavors {
        create("legacy") { dimension = "browserEngine" }
        create("gecko") {
            dimension = "browserEngine"
            ndk { abiFilters.add("arm64-v8a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Gecko browser is confined to the opt-in arm64 flavor. Legacy does not ship it.
    add("geckoImplementation", "org.mozilla.geckoview:geckoview-arm64-v8a:156.0.20260921121718")
    // Stable AndroidX WebKit 1.18.0 (2026-11-18). Service-worker request interception.
    implementation("androidx.webkit:webkit:1.18.0")
    // Browser-backed Custom Tabs share the default browser's sign-in state,
    // while the ad-filtering WebView keeps a separate cookie jar.
    implementation("androidx.browser:browser:1.10.0")
}
