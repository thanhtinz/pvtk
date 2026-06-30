plugins {
    application
}

dependencies {
    implementation(project(":client:core"))
}

application {
    mainClass.set("vn.pvtk.client.java.ConsoleClient")
}

// Run from the repo root (consistent with the other launchers).
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`   // interactive console (m/s/who/quit commands)
}
