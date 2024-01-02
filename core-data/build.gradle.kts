@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

android {
    namespace = "ru.tech.imageresizershrinker.coredata"
    compileSdk = libs.versions.androidCompileSdk.get().toIntOrNull()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toIntOrNull()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = rootProject.extra.get("javaCompile") as JavaVersion
        targetCompatibility = rootProject.extra.get("javaCompile") as JavaVersion
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
    }

    flavorDimensions += "app"

    productFlavors {
        create("foss") {
            dimension = "app"
        }
        create("market") {
            dimension = "app"
        }
        create("jxl") {
            dimension = "app"
        }
    }
}

dependencies {
    implementation(libs.hilt)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.coil)
    implementation(libs.coilCompose)
    implementation(libs.coilGif)
    implementation(libs.coilSvg)
    implementation(libs.avif.coder)
    api(libs.datastore.preferences.android)

    "marketImplementation"(libs.mlkit.segmentation.selfie)
    "jxlImplementation"(libs.mlkit.segmentation.selfie)
    "jxlImplementation"(libs.jxl.coder.coil)

    //TODO REMOVE!!!!!!!!!
    implementation(project(":core-ui"))
    implementation(project(":core-domain"))
}