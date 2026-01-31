import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
    id("com.android.library")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

// Substitute compose dependencies for linuxArm64 with the 9999.0.0-SNAPSHOT versions that have linuxArm64 support
// This is needed because the standard compose libs at 1.9.0 don't have linuxArm64 variants
// Only apply to linuxArm64-specific compile/runtime configurations, not metadata or other platforms
configurations.matching { config ->
    val name = config.name.lowercase()
    // Only match linuxArm64 compile and runtime configurations, exclude metadata
    (name.contains("linuxarm64") && !name.contains("metadata")) &&
    (name.contains("compil") || name.contains("runtime") || name.contains("klib"))
}.all {
    resolutionStrategy.eachDependency {
        // Upgrade all compose dependencies to 9999.0.0-SNAPSHOT for linuxArm64 configurations
        if (requested.group.startsWith("org.jetbrains.compose")) {
            useVersion("9999.0.0-SNAPSHOT")
            because("Using locally built Compose artifacts with linuxArm64 support")
        }
        // Also ensure Skiko uses the EGL-enabled version
        if (requested.group == "org.jetbrains.skiko" && requested.name.contains("linuxarm64")) {
            useVersion("0.9.37.3-egl-SNAPSHOT")
            because("Using EGL-enabled Skiko build for linuxArm64")
        }
    }
}

kotlin {
    // Toggle web targets when needed; default off to avoid JS/Wasm toolchain churn while publishing native artifacts
    val enableWebTargets = providers.gradleProperty("enableWebTargets")
        .map { it.toBoolean() }
        .getOrElse(false)

    jvm("desktop")
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxArm64()
    if (enableWebTargets) {
        js {
            browser {
                testTask(Action {
                    enabled = false
                })
            }
        }

        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            compilations.getByName("test").compileTaskProvider.configure {
                // https://youtrack.jetbrains.com/issue/KT-69014
                compilerOptions.freeCompilerArgs.add("-Xwasm-enable-array-range-checks")
            }
            browser {
                testTask(Action {
                    useKarma {
                        useChromeHeadless()
                        useConfigDirectory(project.projectDir.resolve("karma.config.d").resolve("wasm"))
                    }
                })
            }
            binaries.executable()
        }
    }
    macosX64()
    macosArm64()

    // Don't use default hierarchy - we need custom handling for linuxArm64
    // applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("org.jetbrains.compose.resources.InternalResourceApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        //          common
        //       ┌────┴────┐
        //    skiko       blocking
        //      │      ┌─────┴────────────────┐
        //  ┌───┴───┬──│────────┐             │
        //  │   skikoNative     │      jvmAndAndroid
        //  │    ┌───┴───┐      │        ┌────┼────┐
        // web   ios    macos  desktop android  linuxArm64

        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui.test)
            }
        }
        val blockingMain by creating {
            dependsOn(commonMain)
        }
        val blockingTest by creating {
            dependsOn(commonTest)
        }
        val skikoMain by creating {
            dependsOn(commonMain)
        }
        val skikoTest by creating {
            dependsOn(commonTest)
        }
        val jvmAndAndroidMain by creating {
            dependsOn(blockingMain)
        }
        val jvmAndAndroidTest by creating {
            dependsOn(blockingTest)
        }
        val desktopMain by getting {
            dependsOn(skikoMain)
            dependsOn(jvmAndAndroidMain)
        }
        val desktopTest by getting {
            dependsOn(skikoTest)
            dependsOn(jvmAndAndroidTest)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                //it will be called only in android instrumented tests where the library should be available
                compileOnly(libs.androidx.test.monitor)
            }
        }
        val androidInstrumentedTest by getting {
            dependsOn(jvmAndAndroidTest)
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.compose.ui.test)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.androidx.compose.ui.test.junit4)
            }
        }
        val androidUnitTest by getting {
            dependsOn(jvmAndAndroidTest)
        }

        // Native main - provides Darwin-based XML parsing (NSXMLParser)
        // Used by iOS and macOS targets
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Darwin-native targets (iOS, macOS) - have Foundation APIs and Skiko
        val darwinMain by creating {
            dependsOn(skikoMain)
            dependsOn(blockingMain)
            dependsOn(nativeMain)
        }
        val darwinTest by creating {
            dependsOn(skikoTest)
            dependsOn(blockingTest)
            dependsOn(nativeTest)
        }

        // iOS targets
        val iosMain by creating {
            dependsOn(darwinMain)
        }
        val iosTest by creating {
            dependsOn(darwinTest)
        }
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        val iosX64Test by getting {
            dependsOn(iosTest)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        // macOS targets
        val macosMain by creating {
            dependsOn(darwinMain)
        }
        val macosTest by creating {
            dependsOn(darwinTest)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        val macosX64Test by getting {
            dependsOn(macosTest)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }
        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

        // linuxArm64 - has Skiko (EGL version) but NOT Darwin Foundation APIs
        // Uses explicit platform-specific dependencies because it has its own implementations
        // (can't use skikoMain because it conflicts with linuxArm64-specific code)
        // The resolution strategy handles upgrading compose deps to 9999.0.0-SNAPSHOT for linuxArm64
        val linuxArm64Main by getting {
            dependsOn(blockingMain)
            dependencies {
                // Direct linuxArm64 artifacts - these provide Skiko transitively
                implementation("org.jetbrains.compose.foundation:foundation-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.runtime:runtime-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.ui:ui-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.ui:ui-graphics-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.ui:ui-unit-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.ui:ui-geometry-linuxarm64:9999.0.0-SNAPSHOT")
                implementation("org.jetbrains.compose.ui:ui-text-linuxarm64:9999.0.0-SNAPSHOT")
            }
        }
        val linuxArm64Test by getting {
            dependsOn(blockingTest)
        }

        // Web targets (JS, WASM) - optional to allow native-only publishing
        if (enableWebTargets) {
            val webMain by creating {
                dependsOn(skikoMain)
                dependencies {
                    implementation(libs.kotlinx.browser)
                }
            }
            val webTest by creating {
                dependsOn(skikoTest)
            }
            val jsMain by getting {
                dependsOn(webMain)
            }
            val jsTest by getting {
                dependsOn(webTest)
            }
            val wasmJsMain by getting {
                dependsOn(webMain)
            }
            val wasmJsTest by getting {
                dependsOn(webTest)
            }
        }
    }
}

android {
    compileSdk = 35
    namespace = "org.jetbrains.compose.components.resources"
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel5").apply {
                    device = "Pixel 5"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }
            }
        }
    }
    sourceSets {
        val commonTestResources = "src/commonTest/resources"
        named("androidTest") {
            resources.srcDir(commonTestResources)
            assets.srcDir("src/androidInstrumentedTest/assets")
        }
        named("test") { resources.srcDir(commonTestResources) }
        named("main") { manifest.srcFile("src/androidMain/AndroidManifest.xml") }
    }
}

configureMavenPublication(
    groupId = "org.jetbrains.compose.components",
    artifactId = "components-resources",
    name = "Resources for Compose JB"
)

apiValidation {
    @OptIn(ExperimentalBCVApi::class)
    klib { enabled = true }
    nonPublicMarkers.add("org.jetbrains.compose.resources.InternalResourceApi")
}

//utility task to generate CLDRPluralRuleLists.kt file by 'CLDRPluralRules/plurals.xml'
tasks.register<GeneratePluralRuleListsTask>("generatePluralRuleLists") {
    val projectDir = project.layout.projectDirectory
    pluralsFile = projectDir.file("CLDRPluralRules/plurals.xml")
    outputFile = projectDir.file("src/commonMain/kotlin/org/jetbrains/compose/resources/plural/CLDRPluralRuleLists.kt")
    samplesOutputFile = projectDir.file("src/commonTest/kotlin/org/jetbrains/compose/resources/CLDRPluralRuleLists.test.kt")
}

tasks {
    val desktopTestProcessResources =
        named<ProcessResources>("desktopTestProcessResources")

    withType<Test> {
        dependsOn(desktopTestProcessResources)
        environment("RESOURCES_PATH", desktopTestProcessResources.map { it.destinationDir.absolutePath }.get())
    }
}
