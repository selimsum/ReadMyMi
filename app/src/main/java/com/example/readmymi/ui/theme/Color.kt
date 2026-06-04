package com.example.readmymi.ui.theme

import androidx.compose.ui.graphics.Color

// ────────────────────────────────────────────────────────────────────────────
// MD3 Tonal Palette – seed: Teal #006B5E  (environmental-monitoring feel)
// Generated following Material Design 3 tonal-spot algorithm reference values.
// ────────────────────────────────────────────────────────────────────────────

// ── Primary (Teal) ──────────────────────────────────────────────────────────
val md_primary_light           = Color(0xFF006B5E)
val md_onPrimary_light         = Color(0xFFFFFFFF)
val md_primaryContainer_light  = Color(0xFF74F8DF)
val md_onPrimaryContainer_light = Color(0xFF00201B)

val md_primary_dark            = Color(0xFF53DBC4)
val md_onPrimary_dark          = Color(0xFF003830)
val md_primaryContainer_dark   = Color(0xFF005046)
val md_onPrimaryContainer_dark = Color(0xFF74F8DF)

// ── Secondary (Blue-Grey) ──────────────────────────────────────────────────
val md_secondary_light           = Color(0xFF4B635C)
val md_onSecondary_light         = Color(0xFFFFFFFF)
val md_secondaryContainer_light  = Color(0xFFCDE8DF)
val md_onSecondaryContainer_light = Color(0xFF07201A)

val md_secondary_dark            = Color(0xFFB1CCC3)
val md_onSecondary_dark          = Color(0xFF1D352F)
val md_secondaryContainer_dark   = Color(0xFF334B45)
val md_onSecondaryContainer_dark = Color(0xFFCDE8DF)

// ── Tertiary (Blue accent – chart / data contrast) ─────────────────────────
val md_tertiary_light           = Color(0xFF416276)
val md_onTertiary_light         = Color(0xFFFFFFFF)
val md_tertiaryContainer_light  = Color(0xFFC4E7FF)
val md_onTertiaryContainer_light = Color(0xFF001E2F)

val md_tertiary_dark            = Color(0xFFA8CBE2)
val md_onTertiary_dark          = Color(0xFF0E3446)
val md_tertiaryContainer_dark   = Color(0xFF284A5E)
val md_onTertiaryContainer_dark = Color(0xFFC4E7FF)

// ── Error ───────────────────────────────────────────────────────────────────
val md_error_light             = Color(0xFFBA1A1A)
val md_onError_light           = Color(0xFFFFFFFF)
val md_errorContainer_light    = Color(0xFFFFDAD6)
val md_onErrorContainer_light  = Color(0xFF410002)

val md_error_dark              = Color(0xFFFFB4AB)
val md_onError_dark            = Color(0xFF690005)
val md_errorContainer_dark     = Color(0xFF93000A)
val md_onErrorContainer_dark   = Color(0xFFFFDAD6)

// ── Surfaces ────────────────────────────────────────────────────────────────
val md_surface_light               = Color(0xFFF5FBF7)
val md_onSurface_light             = Color(0xFF171D1B)
val md_onSurfaceVariant_light      = Color(0xFF3F4945)
val md_surfaceContainerLowest_light = Color(0xFFFFFFFF)
val md_surfaceContainerLow_light   = Color(0xFFEFF5F1)
val md_surfaceContainer_light      = Color(0xFFE9EFEB)
val md_surfaceContainerHigh_light  = Color(0xFFE3E9E5)
val md_surfaceContainerHighest_light = Color(0xFFDEE3DF)
val md_surfaceDim_light            = Color(0xFFD5DBD7)
val md_surfaceBright_light         = Color(0xFFF5FBF7)
val md_inverseSurface_light        = Color(0xFF2B312F)
val md_inverseOnSurface_light      = Color(0xFFECF2EE)
val md_inversePrimary_light        = Color(0xFF53DBC4)

val md_surface_dark                = Color(0xFF0E1513)
val md_onSurface_dark              = Color(0xFFDEE3DF)
val md_onSurfaceVariant_dark       = Color(0xFFBFC9C4)
val md_surfaceContainerLowest_dark = Color(0xFF090F0D)
val md_surfaceContainerLow_dark    = Color(0xFF171D1B)
val md_surfaceContainer_dark       = Color(0xFF1B211F)
val md_surfaceContainerHigh_dark   = Color(0xFF252B29)
val md_surfaceContainerHighest_dark = Color(0xFF303634)
val md_surfaceDim_dark             = Color(0xFF0E1513)
val md_surfaceBright_dark          = Color(0xFF333A37)
val md_inverseSurface_dark         = Color(0xFFDEE3DF)
val md_inverseOnSurface_dark       = Color(0xFF2B312F)
val md_inversePrimary_dark         = Color(0xFF006B5E)

// ── Outline ─────────────────────────────────────────────────────────────────
val md_outline_light           = Color(0xFF6F7975)
val md_outlineVariant_light    = Color(0xFFBFC9C4)

val md_outline_dark            = Color(0xFF89938E)
val md_outlineVariant_dark     = Color(0xFF3F4945)

// ── Semantic / app-specific (not part of MD3 spec) ─────────────────────────
val md_success         = Color(0xFF2E7D32)
val md_successDark     = Color(0xFF81C784)
val md_warning         = Color(0xFFD32F2F)
val md_warningDark     = Color(0xFFEF5350)
val md_chartTemperature = Color(0xFFFF5722)
val md_chartHumidity    = Color(0xFF03A9F4)

// ── Theme-aware semantic color accessors ───────────────────────────────────
@androidx.compose.runtime.Composable
fun successColor(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) md_successDark else md_success

@androidx.compose.runtime.Composable
fun dangerColor(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) md_warningDark else md_warning
