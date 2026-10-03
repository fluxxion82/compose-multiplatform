package org.jetbrains.compose.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LinuxResourcesTest {
    @Test
    fun resourceTaskNameIncludesTargetAndBinary() {
        val x64Task = linuxResourceTaskName(
            prefix = "syncComposeResourcesFor",
            targetName = "linuxX64",
            binaryName = "debugExecutable"
        )
        val arm64Task = linuxResourceTaskName(
            prefix = "syncComposeResourcesFor",
            targetName = "linuxArm64",
            binaryName = "debugExecutable"
        )

        assertEquals("syncComposeResourcesForLinuxX64DebugExecutable", x64Task)
        assertEquals("syncComposeResourcesForLinuxArm64DebugExecutable", arm64Task)
        assertNotEquals(x64Task, arm64Task)
    }
}
