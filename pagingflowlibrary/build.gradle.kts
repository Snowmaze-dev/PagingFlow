import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

kotlin {
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
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "pagingflowlibrary"
            isStatic = true
        }
    }
    jvm {
        tasks.getByName("assemble").dependsOn("jvmSourcesJar")
        kotlin {
            jvmToolchain(11)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                //put your multiplatform dependencies here
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            version = "1.0.0"
            group = "ru.snowmaze.pagingflow"
            val split = artifactId.split("-")
            val flavor = split.getOrNull(1)
            val postfix = if (flavor == null) "" else "-$flavor"
            artifactId = "common$postfix"
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
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
