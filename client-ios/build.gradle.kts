// iOS client launcher (RoboVM). Builds only on macOS with the RoboVM toolchain
// and PVTK_BUILD_IOS=1 (see settings.gradle.kts). Provided as a complete scaffold.
plugins {
    java
}

dependencies {
    implementation(project(":client-gdx-core"))
    implementation(libs.gdx.backend.robovm)
    implementation(variantOf(libs.gdx.platform) { classifier("natives-ios") })
}

// The RoboVM Gradle plugin (`com.mobidevelop.robovm`) wires `launchIPhoneSimulator`,
// `launchIOSDevice` and `createIPA` tasks when applied on macOS. It is intentionally
// not applied here so the project configures on Linux CI; enable it locally:
//
//   plugins { id("com.mobidevelop.robovm") version "2.3.21" }
