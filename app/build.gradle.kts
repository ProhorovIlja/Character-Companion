plugins {
    id("com.android.application")
}

android {
    namespace = "dev.caicompanion.app"
    // Android 17 SDK installs under platforms/android-37.0, not android-37.
    // Pin minor API level 0 to avoid resolving another installed 37.x release.
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "dev.caicompanion.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 33
        versionName = "0.33.0-rc1"
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
    // AGP 9.3 provides built-in Kotlin (KGP 2.4.21 is pinned at the root); JVM target follows Java 17 compileOptions.
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
    // Keep the application Kotlin libraries aligned with the actual compiler.
    // AndroidX Core's transitive stdlib 2.4.10 caused build #5 to fail with KGP 2.2.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.4.21"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.core:core-ktx:1.19.1")
    // Gecko browser is confined to the opt-in arm64 flavor. Legacy does not ship it.
    add("geckoImplementation", "org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711")
    // Stable AndroidX WebKit 1.18.0 (2026-11-18). Service-worker request interception.
    implementation("androidx.webkit:webkit:1.18.0")
    // Browser-backed Custom Tabs share the default browser's sign-in state,
    // while the ad-filtering WebView keeps a separate cookie jar.
    implementation("androidx.browser:browser:1.10.0")
}
