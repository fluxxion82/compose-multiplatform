import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") apply false
}

subprojects {
    version = findProperty("deploy.version")!!

    // Substitute skiko linuxArm64 version for EGL support
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.skiko" &&
                requested.name == "skiko-linuxarm64" &&
                requested.version == "0.9.37.3") {
                useVersion("0.9.37.3-egl-SNAPSHOT")
                because("Using EGL-enabled Skiko build for linuxArm64")
            }
            // Force Kotlin stdlib artifacts to the configured kotlin.version (2.2.10) because
            // 2.2.21 metadata artifacts are not published and break common metadata compilation.
            if (requested.group == "org.jetbrains.kotlin" &&
                requested.name.startsWith("kotlin-stdlib")) {
                useVersion(findProperty("kotlin.version")!!.toString())
                because("Align Kotlin stdlib to kotlin.version to ensure common stdlib artifacts resolve")
            }
        }
    }

    plugins.withId("java") {
        configureIfExists<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11

            withJavadocJar()
            withSourcesJar()
        }
    }

    tasks.withType<KotlinCompile>() {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    plugins.withId("maven-publish") {
        configureIfExists<PublishingExtension> {
            repositories {
                maven {
                    name = "ComposeRepo"
                    setUrl(System.getenv("COMPOSE_REPO_URL"))
                    credentials {
                        username = System.getenv("COMPOSE_REPO_USERNAME")
                        password = System.getenv("COMPOSE_REPO_KEY")
                    }
                }
            }
        }
    }
}
