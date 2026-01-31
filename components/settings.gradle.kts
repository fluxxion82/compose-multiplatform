pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        if (extra["compose.useMavenLocal"] == "true") {
            mavenLocal()
        }
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("com.android.library").version(extra["agp.version"] as String)
        id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.17.0")
    }

    val gradlePluginDir = rootDir.resolve("../gradle-plugins")
    if (gradlePluginDir.exists()) {
        includeBuild(gradlePluginDir)
    }
}

dependencyResolutionManagement {
    repositories {
        // mavenLocal first for compose artifacts
        mavenLocal()
        // Custom local maven for linuxArm64 compose artifacts
        maven {
            url = uri(rootDir.resolve("../../../bitchatKmp/apps/embedded/maven"))
        }
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    versionCatalogs {
        create("libs") {
            version("compose", extra["compose.version"].toString())
        }
    }

}

include(":SplitPane:library")
include(":SplitPane:demo")
include(":AnimatedImage:library")
include(":AnimatedImage:demo")
include(":resources:library")
include(":resources:demo:androidApp")
include(":resources:demo:desktopApp")
include(":resources:demo:shared")
include(":ui-tooling-preview:library")
include(":ui-tooling-preview:demo:desktopApp")
include(":ui-tooling-preview:demo:shared")
