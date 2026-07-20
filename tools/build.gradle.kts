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

// Decodes ui/*.ui screen layouts and paints each widget tree as a wireframe PNG.
tasks.register<JavaExec>("exportUi") {
    group = "verification"
    description = "Render original .ui screen layouts to wireframe PNGs"
    workingDir = rootProject.projectDir
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vn.pvtk.tools.UiLayoutExporter")
}
