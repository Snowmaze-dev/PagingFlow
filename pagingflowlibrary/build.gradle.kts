plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

kotlin {
    jvmToolchain(11)
    androidTarget {
        publishAllLibraryVariants()
    }
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

    js(IR) {
        browser {
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "pagingflowlibrary"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.difference)
            implementation(libs.androidx.collection.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.datetime)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        val commonJvmMain = getByName("commonJvmMain") {
            kotlin.srcDir("src/commonJvmMain/kotlin")
        }
        commonJvmMain.dependencies {
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
                version = "1.1.0.1-alpha"
                group = "ru.snowmaze.pagingflow"
                val postfix = if (name == "androidRelease") "android" else name
                artifactId = "common-$postfix"
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
        jvmToolchain(11)
    }
}
