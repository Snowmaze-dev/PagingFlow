plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())


    jvm()
    androidTarget {
        android {
            compileSdk = 36
            namespace = "ru.snowmaze.pagingflow.sample"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.pagingflowlibrary)
            api(libs.kotlinx.coroutines.core)
            api(libs.metro.viewModel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}