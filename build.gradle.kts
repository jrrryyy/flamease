// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Just define the plugin here without applying it
    id("com.google.gms.google-services") version "4.4.1" apply false
}