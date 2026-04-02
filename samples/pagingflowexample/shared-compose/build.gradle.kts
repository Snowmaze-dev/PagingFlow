plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    jvm()
    androidTarget {
        android {
            namespace = "ru.snowmaze.pagingflow.sample"
            compileSdk = 36
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.samples.pagingflowexample.shared)
            implementation(projects.pagingflowlibrary)

            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.util)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)

            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.metro.viewModel.compose)
            implementation(libs.kotlinx.coroutines.core)

        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}