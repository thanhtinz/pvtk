// Root build configuration shared by every JVM subproject.
plugins {
    java
}

tasks.wrapper {
    gradleVersion = "8.14.3"
    distributionType = Wrapper.DistributionType.BIN
    // The build sandbox cannot HEAD services.gradle.org; end users can.
    validateDistributionUrl = false
}

allprojects {
    group = "vn.pvtk"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
    }
}

// Apply common Java settings to the pure-JVM modules. The libGDX Android/iOS
// modules manage their own toolchains, so they opt in explicitly.
subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.add("-parameters")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
