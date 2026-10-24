plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
val pleosSdkEnabled = providers.gradleProperty("pleosSdk").orNull == "true"
android {
    namespace = "io.cabinos.one"
    compileSdk = 35
    useLibrary("android.car", false)
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.cabinos.one"
        minSdk = 29
        targetSdk = 35
        versionCode = 17
        versionName = "0.11.0"
        buildConfigField("boolean", "PLEOS_SDK_ENABLED", pleosSdkEnabled.toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").java.srcDir(if (pleosSdkEnabled) "src/pleos/java" else "src/withoutPleos/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Official SDK service reconnection workers are not reset by Robolectric sandboxes.
            // Keep JVM classes isolated; this is not an emulated successful vehicle service.
            if (pleosSdkEnabled) test.forkEvery = 1
            if (!project.hasProperty("liveRoadTiles")) test.exclude("**/RoadMapLiveTest.class")
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val testHome = layout.buildDirectory.dir("robolectric-home").get().asFile
            testHome.mkdirs()
            test.systemProperty("user.home", testHome.absolutePath)
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts").forEach { key ->
                System.getProperty(key)?.let { value -> test.systemProperty(key, value) }
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    if (pleosSdkEnabled) {
        implementation("ai.pleos.playground:Vehicle:2.3.2")
        implementation("ai.pleos.playground:NaviHelper:3.0.5")
    }
    implementation(project(":core"))
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
