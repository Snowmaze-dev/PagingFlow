@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
//  targetHierarchy.default()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.pagingflowlibrary)
            api(libs.kotlinx.coroutines.core)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            api("dev.icerock.moko:mvvm-core:0.16.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}