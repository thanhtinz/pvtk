plugins {
    application
}

dependencies {
    implementation(project(":client:game"))
    implementation(libs.gdx.backend.lwjgl3)
    // Desktop natives for the current platform.
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("vn.pvtk.client.desktop.DesktopLauncher")
}

// libGDX resolves Gdx.files.internal(...) relative to the working directory.
// Run from the repo root so the shared assets/ tree (ani/, map/, ui/, ...) loads.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
