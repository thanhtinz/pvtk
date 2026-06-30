plugins {
    `java-library`
}

dependencies {
    api(project(":protocol"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(project(":server"))
}
