plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.netty.all)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("vn.pvtk.server.ServerMain")
}
