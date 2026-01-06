// Top-level build.gradle.kts
plugins {
    // Android + Kotlin (সব মডিউলে শেয়ার হবে)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false

    // Maven Publish (GitHub / Maven Central এর জন্য)
    id("com.vanniktech.maven.publish") version "0.35.0" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}