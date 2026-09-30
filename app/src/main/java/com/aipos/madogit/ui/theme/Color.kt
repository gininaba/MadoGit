package com.aipos.madogit.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Material You (Material 3) Tonal Palettes
// ==========================================

// Dark Theme Colors (Fallback developer theme: Sapphire blue & Emerald accents)
val md_theme_dark_primary = Color(0xFF58A6FF)
val md_theme_dark_onPrimary = Color(0xFF002D6E)
val md_theme_dark_primaryContainer = Color(0xFF1F6FEB)
val md_theme_dark_onPrimaryContainer = Color(0xFFDDF4FF)

val md_theme_dark_secondary = Color(0xFF3FB950)
val md_theme_dark_onSecondary = Color(0xFF003A12)
val md_theme_dark_secondaryContainer = Color(0xFF238636)
val md_theme_dark_onSecondaryContainer = Color(0xFFDAFBE1)

val md_theme_dark_tertiary = Color(0xFFBC8CFF)
val md_theme_dark_onTertiary = Color(0xFF3A1869)
val md_theme_dark_tertiaryContainer = Color(0xFF442E69)
val md_theme_dark_onTertiaryContainer = Color(0xFFF0E8FF)

val md_theme_dark_error = Color(0xFFF85149)
val md_theme_dark_errorContainer = Color(0xFF6E161D)
val md_theme_dark_onError = Color(0xFF490206)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF0D1117)
val md_theme_dark_onBackground = Color(0xFFF0F6FC)
val md_theme_dark_surface = Color(0xFF161B22)
val md_theme_dark_onSurface = Color(0xFFF0F6FC)
val md_theme_dark_surfaceVariant = Color(0xFF21262D)
val md_theme_dark_onSurfaceVariant = Color(0xFF8B949E)
val md_theme_dark_outline = Color(0xFF30363D)
val md_theme_dark_outlineVariant = Color(0xFF21262D)

val md_theme_dark_surfaceContainerLowest = Color(0xFF090D12)
val md_theme_dark_surfaceContainerLow = Color(0xFF10141B)
val md_theme_dark_surfaceContainer = Color(0xFF161B22)
val md_theme_dark_surfaceContainerHigh = Color(0xFF21262D)
val md_theme_dark_surfaceContainerHighest = Color(0xFF30363D)

// Light Theme Colors (Clean developer light palette)
val md_theme_light_primary = Color(0xFF0969DA)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFDDF4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001D47)

val md_theme_light_secondary = Color(0xFF1A7F37)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDAFBE1)
val md_theme_light_onSecondaryContainer = Color(0xFF002209)

val md_theme_light_tertiary = Color(0xFF8250DF)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFF0E8FF)
val md_theme_light_onTertiaryContainer = Color(0xFF2E0966)

val md_theme_light_error = Color(0xFFCF222E)
val md_theme_light_errorContainer = Color(0xFFFFEBE9)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF4C000B)

val md_theme_light_background = Color(0xFFF6F8FA)
val md_theme_light_onBackground = Color(0xFF1F2328)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF1F2328)
val md_theme_light_surfaceVariant = Color(0xFFEAEFF5)
val md_theme_light_onSurfaceVariant = Color(0xFF656D76)
val md_theme_light_outline = Color(0xFFD0D7DE)
val md_theme_light_outlineVariant = Color(0xFFE1E4E8)

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF6F8FA)
val md_theme_light_surfaceContainer = Color(0xFFEEF2F6)
val md_theme_light_surfaceContainerHigh = Color(0xFFE5EBF1)
val md_theme_light_surfaceContainerHighest = Color(0xFFD8E1E8)

// ==========================================
// Programming Language Palette (GitHub Standard)
// ==========================================
val LangKotlin = Color(0xFFA97BFF)
val LangTypeScript = Color(0xFF3178C6)
val LangJavaScript = Color(0xFFF1E05A)
val LangPython = Color(0xFF3572A5)
val LangRust = Color(0xFFDEA584)
val LangGo = Color(0xFF00ADD8)
val LangJava = Color(0xFFB07219)
val LangSwift = Color(0xFFF05138)
val LangCpp = Color(0xFFF34B7D)
val LangRuby = Color(0xFF701516)
val LangPhp = Color(0xFF4F5D95)
val LangDart = Color(0xFF00B4AB)
val LangCsharp = Color(0xFF178600)
val LangHtml = Color(0xFFE34C26)
val LangCss = Color(0xFF563D7C)
val LangShell = Color(0xFF89E051)
val LangDefault = Color(0xFF8B949E)

fun getLanguageColor(language: String?): Color {
    return when (language?.lowercase()?.trim()) {
        "kotlin" -> LangKotlin
        "typescript", "ts" -> LangTypeScript
        "javascript", "js" -> LangJavaScript
        "python", "py" -> LangPython
        "rust", "rs" -> LangRust
        "go", "golang" -> LangGo
        "java" -> LangJava
        "swift" -> LangSwift
        "c++", "cpp" -> LangCpp
        "ruby", "rb" -> LangRuby
        "php" -> LangPhp
        "dart" -> LangDart
        "c#", "csharp" -> LangCsharp
        "html" -> LangHtml
        "css", "scss" -> LangCss
        "shell", "bash", "zsh" -> LangShell
        else -> LangDefault
    }
}

// ==========================================
// GitHub Developer Legacy Brand Palette (Fallback & Compatibility)
// ==========================================
val GhBackground = Color(0xFF0D1117)
val GhSurface = Color(0xFF161B22)
val GhSurfaceVariant = Color(0xFF21262D)
val GhBorder = Color(0xFF30363D)
val GhBorderSubtle = Color(0xFF21262D)

// Accents
val GhGreen = Color(0xFF238636)
val GhGreenBright = Color(0xFF2EA44F)
val GhGreenText = Color(0xFF3FB950)
val GhGreenSubtle = Color(0xFF193222)

val GhBlue = Color(0xFF1F6FEB)
val GhBlueBright = Color(0xFF58A6FF)
val GhBlueSubtle = Color(0xFF16233B)

val GhPurple = Color(0xFF8957E5)
val GhPurpleBright = Color(0xFFA371F7)
val GhPurpleSubtle = Color(0xFF281E3B)

val GhRed = Color(0xFFDA3633)
val GhRedBright = Color(0xFFF85149)
val GhRedSubtle = Color(0xFF3A1C1D)

val GhYellow = Color(0xFFD29922)
val GhYellowBright = Color(0xFFE3B341)
val GhYellowSubtle = Color(0xFF352B18)

// Text Colors
val GhTextPrimary = Color(0xFFF0F6FC)
val GhTextSecondary = Color(0xFF8B949E)
val GhTextMuted = Color(0xFF6E7681)
val GhTextTertiary = Color(0xFF484F58)

// Light Theme Alternates
val GhLightBackground = Color(0xFFF6F8FA)
val GhLightSurface = Color(0xFFFFFFFF)
val GhLightSurfaceVariant = Color(0xFFEAEFF5)
val GhLightBorder = Color(0xFFD0D7DE)
val GhLightTextPrimary = Color(0xFF1F2328)
val GhLightTextSecondary = Color(0xFF656D76)
