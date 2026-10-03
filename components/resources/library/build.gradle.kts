import kotlinx.validation.ExperimentalBCVApi
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

val composeVersion = libs.versions.compose.get()
val material3Version = libs.versions.material3.get()
val embeddedTargetsOnly = providers.gradleProperty("compose.resources.embeddedTargetsOnly")
    .map(String::toBoolean)
    .getOrElse(false)
val upstreamComposeVersion = "1.12.1"

// Fork publications sort below upstream releases. Force Linux dependencies to the staged fork.
configurations.matching { config ->
    val name = config.name.lowercase()
    name.contains("linuxarm64") &&
    (name.contains("compil") || name.contains("runtime") || name.contains("klib"))
}.all {
    resolutionStrategy.eachDependency {
        when {
            requested.group == "org.jetbrains.compose.material3" -> {
                useVersion(material3Version)
                because("Using staged Compose Material3 artifacts with linuxArm64 support")
            }
            requested.group.startsWith("org.jetbrains.compose") &&
                requested.group != "org.jetbrains.compose.annotation-internal" &&
                requested.group != "org.jetbrains.compose.collection-internal" -> {
                useVersion(composeVersion)
                because("Using staged Compose artifacts with linuxArm64 support")
            }
        }
    }
}

kotlin {
    jvm("desktop")
    if (!embeddedTargetsOnly) {
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
        iosArm64()
        iosSimulatorArm64()
    }
    linuxArm64()
    if (!embeddedTargetsOnly) {
        js {
            browser {
                testTask {
                    useKarma { useChromeHeadless() }
                }
            }
            binaries.executable()
        }

        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            compilations.getByName("test").compileTaskProvider.configure {
                // https://youtrack.jetbrains.com/issue/KT-69014
                compilerOptions.freeCompilerArgs.add("-Xwasm-enable-array-range-checks")
            }
            browser {
                testTask {
                    useKarma { useChromeHeadless() }
                }
            }
            binaries.executable()
        }
        macosArm64()
    }

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
                if (embeddedTargetsOnly) {
                    compileOnly("org.jetbrains.compose.runtime:runtime:$upstreamComposeVersion")
                    compileOnly("org.jetbrains.compose.foundation:foundation:$upstreamComposeVersion")
                } else {
                    implementation(libs.compose.runtime)
                    implementation(libs.compose.foundation)
                }
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
        val pureKotlinXmlMain by creating {
            dependsOn(commonMain)
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
            dependsOn(pureKotlinXmlMain)
            if (embeddedTargetsOnly) {
                dependencies {
                    implementation("org.jetbrains.compose.runtime:runtime:$upstreamComposeVersion")
                    implementation("org.jetbrains.compose.foundation:foundation:$upstreamComposeVersion")
                }
            }
        }
        val desktopTest by getting {
            dependsOn(skikoTest)
            dependsOn(jvmAndAndroidTest)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        if (!embeddedTargetsOnly) {
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
        }

        if (!embeddedTargetsOnly) {
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
            val macosArm64Main by getting {
                dependsOn(macosMain)
            }
            val macosArm64Test by getting {
                dependsOn(macosTest)
            }
        }

        // linuxArm64 has its own resource implementations and no Darwin Foundation APIs.
        val linuxArm64Main by getting {
            dependsOn(blockingMain)
            dependsOn(pureKotlinXmlMain)
            dependencies {
                implementation("org.jetbrains.compose.foundation:foundation-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.runtime:runtime-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-graphics-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-unit-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-geometry-linuxarm64:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-text-linuxarm64:$composeVersion")
            }
        }
        val linuxArm64Test by getting {
            dependsOn(blockingTest)
        }

        if (!embeddedTargetsOnly) {
            // Web targets (JS, WASM)
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
