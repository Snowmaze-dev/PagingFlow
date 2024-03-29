enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PagingFlow"
include(":pagingflowlibrary")
include(":samples:pagingflowexample:shared")
include(":samples:pagingflowexample:android-recycler")
include(":samples:pagingflowexample:android-compose")
