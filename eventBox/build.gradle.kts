plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.ogmediaapps.eventbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Wide on purpose: the library only needs core-ktx and coroutines.
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // Obfuscates and shrinks the library's own internals before it is packaged, so a
            // consumer gets only the public API in readable form. See proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = false // not supported for libraries; the app shrinks them
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro",)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // StateFlow is part of the public API (NextGenEventLog.events / isActiveFlow).
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.JpOriginDev"
                artifactId = "event-box"
                version = System.getenv("VERSION") ?: "1.0.0" // JitPack sets VERSION to the git tag
            }
        }
    }
}
