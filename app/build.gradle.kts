import java.security.MessageDigest
import java.nio.file.Files

plugins {
    id("com.android.application")
}

// GeckoView 159 requires API 37.2. Keep WebView independently buildable on 37.0.
// Manual helpers explicitly select the matching SDK for each flavor.
val companionSdkMinor = providers.gradleProperty("companionSdkMinor").orElse("2").get().toInt()
require(companionSdkMinor in setOf(0, 2)) { "Only Android SDK 37.0 and 37.2 are supported" }

// Embedded preview is explicitly enabled with a local pinned source checkout.
val mandyLlamaDir = providers.gradleProperty("mandyLlamaDir").orNull

// Optional speech library from the explicit isolated native build helper only.
val mandyWhisperDir = providers.gradleProperty("mandyWhisperJniDir").orNull
val mandyWhisperSha = providers.gradleProperty("mandyWhisperSha256").orNull
require((mandyWhisperDir == null) == (mandyWhisperSha == null)) { "Speech staging directory and digest must be supplied together" }
val mandyWhisperStage = mandyWhisperDir?.let { path ->
    require(mandyLlamaDir != null) { "Speech preview requires the independent ARM64 text preview" }
    require(mandyWhisperSha != null && mandyWhisperSha.matches(Regex("[0-9a-f]{64}"))) { "Invalid speech library digest" }
    val directory = rootProject.file(path)
    val abi = directory.resolve("arm64-v8a")
    val binary = abi.resolve("libmandy_whisper.so")
    require(directory.list()?.toList() == listOf("arm64-v8a") &&
            abi.list()?.toList() == listOf("libmandy_whisper.so") &&
            listOf(directory, abi, binary).none { Files.isSymbolicLink(it.toPath()) } &&
            binary.isFile && binary.length() in 1L..(128L * 1024 * 1024)) { "Invalid isolated speech staging layout" }
    val digest = MessageDigest.getInstance("SHA-256")
    binary.inputStream().use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    require(actual == mandyWhisperSha) { "Speech library digest mismatch" }
    directory
}

android {
    if (mandyLlamaDir != null) {
        ndkVersion = "29.0.13113456"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/mandy/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    }
    if (mandyWhisperStage != null) {
        sourceSets.getByName("main").jniLibs.srcDir(mandyWhisperStage)
        // Preserve the exact audited file bytes through APK packaging.
        packaging.jniLibs.keepDebugSymbols.add("**/libmandy_whisper.so")
    }
    namespace = "dev.caicompanion.app"
    // Explicit minor API selection: GeckoView = 37.2; legacy WebView = 37.0.
    compileSdk {
        version = release(37) {
            minorApiLevel = companionSdkMinor
        }
    }
    defaultConfig {
        applicationId = "dev.caicompanion.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 38
        versionName = "0.38.0-rc1"
        buildConfigField("boolean", "MANDY_EMBEDDED_PREVIEW", (mandyLlamaDir != null).toString())
        buildConfigField("boolean", "MANDY_WHISPER_PREVIEW", (mandyWhisperStage != null).toString())
        if (mandyLlamaDir != null) {
            ndk { abiFilters.add("arm64-v8a") }
            externalNativeBuild {
                cmake {
                    arguments += "-DMANDY_LLAMA_DIR=" + rootProject.file(mandyLlamaDir).absolutePath
                    targets += "mandy_llama"
                }
            }
        }
    }
    flavorDimensions += "browserEngine"
    productFlavors {
        create("legacy") {
            dimension = "browserEngine"
            manifestPlaceholders["companionRenderer"] = "legacy"
        }
        create("gecko") {
            dimension = "browserEngine"
            manifestPlaceholders["companionRenderer"] = "gecko"
            ndk { abiFilters.add("arm64-v8a") }
        }
        // Direct native workspace, same application ID and local data paths.
        create("mandy") {
            dimension = "browserEngine"
            manifestPlaceholders["companionRenderer"] = "mandy"
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
