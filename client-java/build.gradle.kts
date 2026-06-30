plugins {
    application
}

dependencies {
    implementation(project(":client-core"))
}

application {
    mainClass.set("vn.pvtk.client.java.ConsoleClient")
}
