// Google's public Maven Central mirror is placed before Maven Central itself.
// GitHub-hosted runners can receive HTTP 429 from repo.maven.apache.org; this
// avoids depending on that host for common buildscript and app artifacts.
// Keep google() first for Android Gradle Plugin and AndroidX artifacts, and
// retain the official Maven Central as a fallback. HTTPS only.
pluginManagement {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        maven(url = "https://maven.mozilla.org/maven2/")
    }
}
rootProject.name = "CharacterCompanion"
include(":app")
