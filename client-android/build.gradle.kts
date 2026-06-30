// Android client launcher. This module only participates in the build when the
// Android SDK is available (see settings.gradle.kts). Requires the Android Gradle
// Plugin, declared in settings.gradle.kts pluginManagement when activated.
plugins {
    id("com.android.application")
}

android {
    namespace = "vn.pvtk.client.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.pvtk.client.android"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Bundle the shared asset tree (sprites, maps, UI, content tables) into the APK.
    sourceSets["main"].assets.srcDirs("../assets")

    // libGDX ships native .so files inside its platform jars.
    packaging {
        resources.excludes.add("META-INF/robovm/ios/robovm.xml")
    }
}

dependencies {
    implementation(project(":client-gdx-core"))
    implementation(libs.gdx.backend.android)
    natives(variantOf(libs.gdx.platform) { classifier("natives-armeabi-v7a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-arm64-v8a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86_64") })
}

// libGDX native extraction into jniLibs (standard libGDX Android setup).
val natives: Configuration by configurations.creating

tasks.register<Copy>("copyAndroidNatives") {
    from({ natives.map { zipTree(it) } })
    into(layout.buildDirectory.dir("jniLibs"))
    include("**/*.so")
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}
