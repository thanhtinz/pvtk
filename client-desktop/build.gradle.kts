plugins {
    application
}

dependencies {
    implementation(project(":client-gdx-core"))
    implementation(libs.gdx.backend.lwjgl3)
    // Desktop natives for the current platform.
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("vn.pvtk.client.desktop.DesktopLauncher")
}
