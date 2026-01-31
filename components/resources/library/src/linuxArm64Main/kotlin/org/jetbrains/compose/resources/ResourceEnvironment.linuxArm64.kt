package org.jetbrains.compose.resources

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun getSystemEnvironment(): ResourceEnvironment {
    // Get locale from environment variables (LANG, LC_ALL, LC_MESSAGES)
    val localeString = getenv("LC_ALL")?.toKString()
        ?: getenv("LC_MESSAGES")?.toKString()
        ?: getenv("LANG")?.toKString()
        ?: "en_US"

    // Parse locale string (e.g., "en_US.UTF-8" -> language="en", region="US")
    val localePart = localeString.substringBefore('.')
    val parts = localePart.split('_', '-')
    val language = parts.getOrNull(0) ?: "en"
    val region = parts.getOrNull(1) ?: ""

    // Detect dark theme - check common desktop environment variables
    // GTK_THEME, GNOME/KDE settings, etc.
    val isDarkTheme = detectDarkTheme()

    // Default DPI for Linux - typically 96 DPI
    // In a real implementation, you might query X11/Wayland for actual DPI
    val dpi = getenv("GDK_DPI_SCALE")?.toKString()?.toDoubleOrNull()?.let { (96 * it).toInt() }
        ?: getenv("QT_SCALE_FACTOR")?.toKString()?.toDoubleOrNull()?.let { (96 * it).toInt() }
        ?: 96

    return ResourceEnvironment(
        language = LanguageQualifier(language),
        region = RegionQualifier(region),
        theme = ThemeQualifier.selectByValue(isDarkTheme),
        density = DensityQualifier.selectByValue(dpi)
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun detectDarkTheme(): Boolean {
    // Check GTK theme name for "dark" suffix
    val gtkTheme = getenv("GTK_THEME")?.toKString() ?: ""
    if (gtkTheme.lowercase().contains("dark")) return true

    // Check color scheme preference (freedesktop standard)
    val colorScheme = getenv("COLOR_SCHEME")?.toKString() ?: ""
    if (colorScheme == "prefer-dark") return true

    // Default to light theme
    return false
}
