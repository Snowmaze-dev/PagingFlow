plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

kotlin {
    jvmToolchain(11)
    android {
        publishAllLibraryVariants()
    }
    jvm()
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
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.difference)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
        }
        val androidTest by getting {
            dependsOn(commonTest)
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                version = "1.0.6-alpha"
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
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }
}
