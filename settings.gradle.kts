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

// --- Server + shared protocol (compiles with JDK 21 + Maven Central only) ---
include(":protocol")        // Shared wire-protocol codec (no external deps)
include(":server")          // Authoritative Netty game server
include(":tools")           // Offline asset tools (sprite-sheet exporter)
include(":web")             // Website + admin panel (HTTP API + static frontend)

// --- Cross-platform client (one game, many launchers — the libGDX layout) ---
// client/core    : networking + game model (pure JVM, no GPU)
// client/java    : headless / console client (PC, pure Java, no GPU)
// client/game    : shared libGDX game (rendering + input), depends on client/core
// client/desktop : PC launcher (Windows / macOS / Linux via LWJGL3)
// client/android : Android launcher (requires the Android SDK)
// client/ios     : iOS launcher (requires RoboVM + macOS toolchain)
//
// Desktop builds anywhere. Android/iOS activate only when their SDKs are present,
// so CI on a bare JDK stays green.
include(":client:core")
include(":client:java")     // headless / console client (PC, pure Java)
include(":client:game")
include(":client:desktop")

if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":client:android")
}
if (System.getProperty("os.name").startsWith("Mac") && System.getenv("PVTK_BUILD_IOS") == "1") {
    include(":client:ios")
}
