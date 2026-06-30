pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Resolved only when the Android module is active.
        id("com.android.application") version "8.5.2"
        // Resolved only when the iOS module is active (macOS).
        id("com.mobidevelop.robovm") version "2.3.21"
    }
}

rootProject.name = "pvtk"

// --- Core multiplayer foundation (compiles with JDK 21 + Maven Central only) ---
include(":protocol")      // Shared wire-protocol codec (no external deps)
include(":server")        // Authoritative Netty game server
include(":client-core")   // Cross-platform client networking + game model (pure JVM)
include(":client-java")   // Headless / Swing reference client (PC, Java)

// --- libGDX cross-platform graphical client ---
// `client-gdx-core`  : shared rendering + input + game screens (depends on :client-core)
// `client-desktop`   : PC launcher (Windows / macOS / Linux via LWJGL3)
// `client-android`   : Android launcher  (requires the Android SDK)
// `client-ios`       : iOS launcher      (requires RoboVM + macOS toolchain)
//
// The desktop module builds anywhere. Android/iOS are scaffolded and only
// activate when their SDKs are present, so CI on a bare JDK stays green.
include(":client-gdx-core")
include(":client-desktop")

if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":client-android")
}
// iOS only builds on macOS with the RoboVM toolchain installed.
if (System.getProperty("os.name").startsWith("Mac") && System.getenv("PVTK_BUILD_IOS") == "1") {
    include(":client-ios")
}
