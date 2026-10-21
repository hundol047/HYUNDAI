plugins { kotlin("multiplatform") }
kotlin {
    jvmToolchain(21)
    jvm()
    js(IR) {
        browser {
            commonWebpackConfig { outputFileName = "engine.js" }
        }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") }
        jvmTest.dependencies { implementation("junit:junit:4.13.2") }
    }
}
tasks.register("test") { dependsOn("jvmTest") }
