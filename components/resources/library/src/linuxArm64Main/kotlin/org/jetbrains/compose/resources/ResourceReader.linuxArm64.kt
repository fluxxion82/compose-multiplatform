package org.jetbrains.compose.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.PATH_MAX
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.readlink

@ExperimentalResourceApi
internal actual fun getPlatformResourceReader(): ResourceReader = LinuxArm64ResourceReader

@ExperimentalResourceApi
internal actual val ProvidableCompositionLocal<ResourceReader>.currentOrPreview: ResourceReader
    @Composable get() = current

@OptIn(ExperimentalForeignApi::class)
@ExperimentalResourceApi
private object LinuxArm64ResourceReader : ResourceReader {

    // Base path for resources - can be overridden via environment variable
    private val resourceBasePath: String by lazy {
        getenv("COMPOSE_RESOURCES_PATH")?.toKString() ?: ""
    }

    // Directory containing the executable, resolved via /proc/self/exe
    private val executableDir: String by lazy {
        memScoped {
            val buffer = allocArray<ByteVar>(PATH_MAX)
            val len = readlink("/proc/self/exe", buffer, PATH_MAX.toULong())
            if (len > 0) {
                // readlink doesn't null-terminate, so extract only the valid bytes
                val bytes = ByteArray(len.toInt()) { i -> buffer[i] }
                val exePath = bytes.decodeToString()
                val dir = exePath.substringBeforeLast('/')
                println("[ResourceReader] executableDir resolved to: $dir")
                dir
            } else {
                println("[ResourceReader] Failed to resolve /proc/self/exe, len=$len")
                ""
            }
        }
    }

    override suspend fun read(path: String): ByteArray {
        val fullPath = resolveResourcePath(path)
        return readFileBytes(fullPath)
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val fullPath = resolveResourcePath(path)
        return readFileBytesPartial(fullPath, offset, size)
    }

    override fun getUri(path: String): String {
        val fullPath = resolveResourcePath(path)
        return "file://$fullPath"
    }

    private fun fileExists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    private fun resolveResourcePath(path: String): String {
        // If path is absolute, use it directly
        if (path.startsWith("/")) return path

        // If base path is set via environment variable, use it
        if (resourceBasePath.isNotEmpty()) {
            val resolved = "$resourceBasePath/$path"
            println("[ResourceReader] Using env path: $resolved")
            return resolved
        }

        // Try multiple fallback paths relative to executable (like macOS/iOS does)
        if (executableDir.isNotEmpty()) {
            val candidates = listOf(
                "$executableDir/compose-resources/$path",  // Resources in compose-resources/ subdirectory
                "$executableDir/$path"                     // Resources directly next to executable
            )
            for (candidate in candidates) {
                val exists = fileExists(candidate)
                println("[ResourceReader] Trying: $candidate (exists=$exists)")
                if (exists) return candidate
            }
        } else {
            println("[ResourceReader] executableDir is empty, skipping relative paths")
        }

        // Default: look in current directory (original fallback)
        println("[ResourceReader] Falling back to relative path: $path")
        return path
    }

    private fun readFileBytes(path: String): ByteArray {
        val file = fopen(path, "rb") ?: throw MissingResourceException(path)

        try {
            // Get file size
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            if (size < 0) throw MissingResourceException(path, "Could not determine file size")
            fseek(file, 0, SEEK_SET)

            // Read file content
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                val bytesRead = fread(pinned.addressOf(0), 1u, size.toULong(), file)
                if (bytesRead.toLong() != size) {
                    throw MissingResourceException(path, "Could not read complete file")
                }
            }

            return buffer
        } finally {
            fclose(file)
        }
    }

    private fun readFileBytesPartial(path: String, offset: Long, size: Long): ByteArray {
        val file = fopen(path, "rb") ?: throw MissingResourceException(path)

        try {
            // Seek to offset
            fseek(file, offset, SEEK_SET)

            // Read requested size
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                val bytesRead = fread(pinned.addressOf(0), 1u, size.toULong(), file)
                if (bytesRead.toLong() != size) {
                    throw MissingResourceException(path, "Could not read requested bytes")
                }
            }

            return buffer
        } finally {
            fclose(file)
        }
    }
}
