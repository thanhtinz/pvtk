plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
}

application {
    // Slices the original .fr/.png sprite sheets into individual frame PNGs.
    mainClass.set("vn.pvtk.tools.SpriteExporter")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
