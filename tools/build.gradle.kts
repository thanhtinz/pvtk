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

// Decodes sprite_*.spr animation modules and writes a contact sheet per sprite.
tasks.register<JavaExec>("exportAnim") {
    group = "verification"
    description = "Compose original .spr animation modules into contact-sheet PNGs"
    workingDir = rootProject.projectDir
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vn.pvtk.tools.SprAnimationExporter")
}
