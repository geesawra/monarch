package industries.geesawra.monarch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Blue Heeler theme: sky blue and warm gold, inspired by Bluey.
// Palette: gold #EDCC6F, pale sky #D2EBFF, sky #88CAFC, midnight #404066, deep navy #2B2C41.

private val Gold = Color(0xFFEDCC6F)
private val GoldSoft = Color(0xFFF7E6B5)
private val GoldDeep = Color(0xFF6B5720)
private val PaleSky = Color(0xFFD2EBFF)
private val Sky = Color(0xFF88CAFC)
private val SkyDeep = Color(0xFF4A8BD9)
private val Midnight = Color(0xFF404066)
private val MidnightSoft = Color(0xFF9697AD)
private val Navy = Color(0xFF2B2C41)
private val NavyDeep = Color(0xFF1E1F30)
private val NavyMid = Color(0xFF35364E)
private val NavyHigh = Color(0xFF4E4F78)
private val NearWhite = Color(0xFFFCFDFF)
private val SurfaceLow = Color(0xFFF5F9FD)
private val SurfaceMid = Color(0xFFEAF2FA)
private val SurfaceHigh = Color(0xFFE0EBF5)
private val SurfaceHighest = Color(0xFFD6E5F0)
private val SurfaceDimLight = Color(0xFFDCE7F2)

val BlueHeelerLightColorScheme = lightColorScheme(
    primary = Sky,
    onPrimary = Navy,
    primaryContainer = PaleSky,
    onPrimaryContainer = Navy,
    secondary = Gold,
    onSecondary = Navy,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Navy,
    tertiary = Midnight,
    onTertiary = PaleSky,
    tertiaryContainer = MidnightSoft,
    onTertiaryContainer = Navy,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = NearWhite,
    onBackground = Navy,
    surface = NearWhite,
    onSurface = Navy,
    surfaceVariant = PaleSky,
    onSurfaceVariant = Midnight,
    outline = Midnight,
    outlineVariant = MidnightSoft,
    inverseSurface = Navy,
    inverseOnSurface = PaleSky,
    inversePrimary = SkyDeep,
    surfaceTint = Sky,
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    surfaceDim = SurfaceDimLight,
    surfaceBright = Color(0xFFFFFFFF),
)

val BlueHeelerDarkColorScheme = darkColorScheme(
    primary = Sky,
    onPrimary = Navy,
    primaryContainer = Midnight,
    onPrimaryContainer = PaleSky,
    secondary = Gold,
    onSecondary = Navy,
    secondaryContainer = GoldDeep,
    onSecondaryContainer = GoldSoft,
    tertiary = PaleSky,
    onTertiary = Navy,
    tertiaryContainer = Midnight,
    onTertiaryContainer = PaleSky,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Navy,
    onBackground = PaleSky,
    surface = Navy,
    onSurface = PaleSky,
    surfaceVariant = Midnight,
    onSurfaceVariant = PaleSky,
    outline = MidnightSoft,
    outlineVariant = Midnight,
    inverseSurface = PaleSky,
    inverseOnSurface = Navy,
    inversePrimary = SkyDeep,
    surfaceTint = Sky,
    scrim = Color(0xFF000000),
    surfaceContainerLowest = NavyDeep,
    surfaceContainerLow = Navy,
    surfaceContainer = NavyMid,
    surfaceContainerHigh = Midnight,
    surfaceContainerHighest = NavyHigh,
    surfaceDim = NavyDeep,
    surfaceBright = NavyHigh,
)
