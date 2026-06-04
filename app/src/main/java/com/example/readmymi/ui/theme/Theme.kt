package com.example.readmymi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ── Light scheme (seed: Teal #006B5E) ──────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                = md_primary_light,
    onPrimary              = md_onPrimary_light,
    primaryContainer       = md_primaryContainer_light,
    onPrimaryContainer     = md_onPrimaryContainer_light,
    secondary              = md_secondary_light,
    onSecondary            = md_onSecondary_light,
    secondaryContainer     = md_secondaryContainer_light,
    onSecondaryContainer   = md_onSecondaryContainer_light,
    tertiary               = md_tertiary_light,
    onTertiary             = md_onTertiary_light,
    tertiaryContainer      = md_tertiaryContainer_light,
    onTertiaryContainer    = md_onTertiaryContainer_light,
    error                  = md_error_light,
    onError                = md_onError_light,
    errorContainer         = md_errorContainer_light,
    onErrorContainer       = md_onErrorContainer_light,
    surface                = md_surface_light,
    onSurface              = md_onSurface_light,
    onSurfaceVariant       = md_onSurfaceVariant_light,
    surfaceContainerLowest = md_surfaceContainerLowest_light,
    surfaceContainerLow    = md_surfaceContainerLow_light,
    surfaceContainer       = md_surfaceContainer_light,
    surfaceContainerHigh   = md_surfaceContainerHigh_light,
    surfaceContainerHighest = md_surfaceContainerHighest_light,
    surfaceDim             = md_surfaceDim_light,
    surfaceBright          = md_surfaceBright_light,
    inverseSurface         = md_inverseSurface_light,
    inverseOnSurface       = md_inverseOnSurface_light,
    inversePrimary         = md_inversePrimary_light,
    outline                = md_outline_light,
    outlineVariant         = md_outlineVariant_light,
)

// ── Dark scheme ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                = md_primary_dark,
    onPrimary              = md_onPrimary_dark,
    primaryContainer       = md_primaryContainer_dark,
    onPrimaryContainer     = md_onPrimaryContainer_dark,
    secondary              = md_secondary_dark,
    onSecondary            = md_onSecondary_dark,
    secondaryContainer     = md_secondaryContainer_dark,
    onSecondaryContainer   = md_onSecondaryContainer_dark,
    tertiary               = md_tertiary_dark,
    onTertiary             = md_onTertiary_dark,
    tertiaryContainer      = md_tertiaryContainer_dark,
    onTertiaryContainer    = md_onTertiaryContainer_dark,
    error                  = md_error_dark,
    onError                = md_onError_dark,
    errorContainer         = md_errorContainer_dark,
    onErrorContainer       = md_onErrorContainer_dark,
    surface                = md_surface_dark,
    onSurface              = md_onSurface_dark,
    onSurfaceVariant       = md_onSurfaceVariant_dark,
    surfaceContainerLowest = md_surfaceContainerLowest_dark,
    surfaceContainerLow    = md_surfaceContainerLow_dark,
    surfaceContainer       = md_surfaceContainer_dark,
    surfaceContainerHigh   = md_surfaceContainerHigh_dark,
    surfaceContainerHighest = md_surfaceContainerHighest_dark,
    surfaceDim             = md_surfaceDim_dark,
    surfaceBright          = md_surfaceBright_dark,
    inverseSurface         = md_inverseSurface_dark,
    inverseOnSurface       = md_inverseOnSurface_dark,
    inversePrimary         = md_inversePrimary_dark,
    outline                = md_outline_dark,
    outlineVariant         = md_outlineVariant_dark,
)

// ── MD3 shapes ──────────────────────────────────────────────────────────────
val MD3Shapes = Shapes(
    extraSmall  = RoundedCornerShape(4.dp),
    small       = RoundedCornerShape(8.dp),
    medium      = RoundedCornerShape(12.dp),
    large       = RoundedCornerShape(16.dp),
    extraLarge  = RoundedCornerShape(28.dp),
)

@Composable
fun ReadMyMiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor      = android.graphics.Color.TRANSPARENT
            window.navigationBarColor  = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars     = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = MD3Shapes,
        content     = content,
    )
}
