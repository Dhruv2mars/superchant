package com.superchant.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme - Warm, spiritual, saffron-inspired
// Primary is used for text and icons (section headers, stats, labels) as well as fills, so it is the
// lightest shade of the saffron hue that reaches WCAG AA (4.5:1) on every light background the app
// uses: 5.26 on surface, 4.73 on cards, 4.50 on the goal chip; white text on it is 5.41.
// The original #C4956A (2.22-2.60 as text, 2.67 under white text) stays as the accent (surfaceTint).
val md_theme_light_primary = Color(0xFF8C6136)           // Warm saffron/amber, text-safe
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFFFDDB5)  // Soft golden
val md_theme_light_onPrimaryContainer = Color(0xFF2D1600)

val md_theme_light_secondary = Color(0xFF735B4A)         // Earthy brown
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFFDCC8)
val md_theme_light_onSecondaryContainer = Color(0xFF29180C)

val md_theme_light_tertiary = Color(0xFF5C6236)          // Sage green
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFE1E7AF)
val md_theme_light_onTertiaryContainer = Color(0xFF1A1E00)

val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_background = Color(0xFFFFFBF8)        // Warm off-white
val md_theme_light_onBackground = Color(0xFF1F1B16)
val md_theme_light_surface = Color(0xFFFFFBF8)
val md_theme_light_onSurface = Color(0xFF1F1B16)
val md_theme_light_surfaceVariant = Color(0xFFF2E0D0)    // Warm cream
val md_theme_light_onSurfaceVariant = Color(0xFF504539)
val md_theme_light_outline = Color(0xFF71665B)          // 5.44:1 on surface for hint text (was #827568, 4.35)
val md_theme_light_outlineVariant = Color(0xFFD5C4B5)

val md_theme_light_inverseSurface = Color(0xFF352F2B)
val md_theme_light_inverseOnSurface = Color(0xFFFAEFE7)
val md_theme_light_inversePrimary = Color(0xFFFFB95A)

val md_theme_light_surfaceTint = Color(0xFFC4956A)
val md_theme_light_scrim = Color(0xFF000000)

// Dark Theme - Deep, meditative, golden accents
val md_theme_dark_primary = Color(0xFFFFB95A)            // Golden amber
val md_theme_dark_onPrimary = Color(0xFF4A2800)
val md_theme_dark_primaryContainer = Color(0xFF6A3C00)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDDB5)

val md_theme_dark_secondary = Color(0xFFE3C0AA)          // Soft tan
val md_theme_dark_onSecondary = Color(0xFF412D1E)
val md_theme_dark_secondaryContainer = Color(0xFF5A4434)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFDCC8)

val md_theme_dark_tertiary = Color(0xFFC5CB95)           // Muted sage
val md_theme_dark_onTertiary = Color(0xFF2E330D)
val md_theme_dark_tertiaryContainer = Color(0xFF454A21)
val md_theme_dark_onTertiaryContainer = Color(0xFFE1E7AF)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF16130F)         // Deep warm black
val md_theme_dark_onBackground = Color(0xFFEBE0D9)
val md_theme_dark_surface = Color(0xFF16130F)
val md_theme_dark_onSurface = Color(0xFFEBE0D9)
val md_theme_dark_surfaceVariant = Color(0xFF504539)     // Warm dark
val md_theme_dark_onSurfaceVariant = Color(0xFFD5C4B5)
val md_theme_dark_outline = Color(0xFF9D8E80)
val md_theme_dark_outlineVariant = Color(0xFF504539)

val md_theme_dark_inverseSurface = Color(0xFFEBE0D9)
val md_theme_dark_inverseOnSurface = Color(0xFF352F2B)
val md_theme_dark_inversePrimary = Color(0xFF8B5000)

val md_theme_dark_surfaceTint = Color(0xFFFFB95A)
val md_theme_dark_scrim = Color(0xFF000000)

// Special colors for the app
val CounterGold = Color(0xFFD4A574)
val CounterGoldDark = Color(0xFFFFB95A)
val ProgressTrackLight = Color(0xFFE8DDD4)
val ProgressTrackDark = Color(0xFF3A3330)

// Focus layout - near-black, low-glare palette used regardless of theme
val FocusBackground = Color(0xFF000000)
val FocusTrack = Color(0xFF1F1A16)
val FocusText = Color(0xFF9C8F84)
