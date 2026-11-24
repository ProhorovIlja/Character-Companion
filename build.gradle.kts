// Built-in Kotlin stays enabled. Override AGP's bundled KGP using the
// supported top-level buildscript mechanism; do not apply kotlin-android.
// https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency
buildscript {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
plugins {
    id("com.android.application") version "9.3.1" apply false
}
