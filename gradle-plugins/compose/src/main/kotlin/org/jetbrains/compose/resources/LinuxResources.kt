package org.jetbrains.compose.resources

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.jetbrains.compose.desktop.application.internal.ComposeProperties
import org.jetbrains.compose.internal.utils.dependsOn
import org.jetbrains.compose.internal.utils.registerOrConfigure
import org.jetbrains.compose.internal.utils.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.konan.target.Family

private const val LINUX_COMPOSE_RESOURCES_DIR = "compose-resources"

internal fun Project.configureSyncLinuxComposeResources(
    kotlinExtension: KotlinMultiplatformExtension
) {
    if (ComposeProperties.dontSyncResources(project).get()) {
        logger.info(
            "Compose Multiplatform resource management for Linux is disabled: " +
                    "'${ComposeProperties.SYNC_RESOURCES_PROPERTY}' value is 'false'"
        )
        return
    }

    kotlinExtension.targets.withType(KotlinNativeTarget::class.java).all { nativeTarget ->
        if (nativeTarget.isLinuxTarget()) {
            // Handle regular executables
            nativeTarget.binaries.withType(Executable::class.java).all { executable ->
                val executableResources = files()
                executable.compilation.allKotlinSourceSets.forAll { ss ->
                    executableResources.from(ss.resources.sourceDirectories)
                }

                val syncTaskName = "syncComposeResourcesFor${executable.name.uppercaseFirstChar()}"
                val syncTask = tasks.registerOrConfigure<Copy>(syncTaskName) {
                    from(executableResources)
                    into(executable.outputDirectory.resolve(LINUX_COMPOSE_RESOURCES_DIR))
                }

                executable.linkTaskProvider.dependsOn(syncTask)
            }

            // Handle test executables
            nativeTarget.binaries.withType(TestExecutable::class.java).all { testExec ->
                val copyTestResourcesTask = tasks.registerOrConfigure<Copy>(
                    "copyTestComposeResourcesFor${testExec.target.targetName.uppercaseFirstChar()}"
                ) {
                    from({
                        (testExec.compilation.associatedCompilations + testExec.compilation).flatMap { compilation ->
                            compilation.allKotlinSourceSets.map { it.resources }
                        }
                    })
                    into(testExec.outputDirectory.resolve(LINUX_COMPOSE_RESOURCES_DIR))
                }
                testExec.linkTaskProvider.dependsOn(copyTestResourcesTask)
            }
        }
    }
}

private fun KotlinNativeTarget.isLinuxTarget(): Boolean =
    konanTarget.family == Family.LINUX
