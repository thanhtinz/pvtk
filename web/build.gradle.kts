plugins {
    application
}

dependencies {
    implementation(project(":server"))
    implementation(project(":protocol"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    // Starts the game server AND the website in one process.
    mainClass.set("vn.pvtk.web.WebMain")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
