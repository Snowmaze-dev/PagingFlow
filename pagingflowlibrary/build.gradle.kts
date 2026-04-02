plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
    androidTarget { publishLibraryVariants() }
    jvm()

    applyHierarchyTemplate {
        common {
            group("commonJvm") {
                withJvm()
                withAndroidTarget()
            }
            group("nonJvm") {
                withJs()
                withIosX64()
                withIosArm64()
            }
        }
    }

    listOf(
        wasmJs(),
        js(IR)
    ).forEach {
        it.browser {
            testTask {
                enabled = false
            }
        }
        it.binaries.executable()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "pagingflowlibrary"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.collection)
            implementation(libs.difference)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        val commonJvmMain = getByName("commonJvmMain") {
            kotlin.srcDir("src/commonJvmMain/kotlin")
        }
        commonJvmMain.dependencies {
        }
        val commonJvmTest = getByName("commonJvmTest") {
            kotlin.srcDir("src/commonJvmTest/kotlin")
        }
        commonJvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val nonJvmMain = getByName("nonJvmMain") {
            kotlin.srcDir("src/nonJvmMain/kotlin")
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                version = "1.1.5-alpha1"
                group = "ru.snowmaze.pagingflow"
                artifactId = if (name == "kotlinMultiplatform") "common" else name
            }
        }
    }
}

android {
    namespace = "ru.snowmaze.pagingflow"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    kotlin {
        jvmToolchain(17)
    }
}
