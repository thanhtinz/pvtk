plugins {
    `java-library`
}

dependencies {
    api(project(":client:core"))
    api(libs.gdx.core)
    // TrueType font rendering for Vietnamese (and optional CJK) glyphs.
    api(libs.gdx.freetype)
}
