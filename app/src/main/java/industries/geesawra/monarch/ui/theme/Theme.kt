package industries.geesawra.monarch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import industries.geesawra.monarch.datalayer.AppTheme

@Composable
fun MonarchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    appTheme: AppTheme = AppTheme.Monarch,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> when (appTheme) {
            AppTheme.Monarch -> if (darkTheme) DarkColorScheme else LightColorScheme
            AppTheme.Bluesky -> if (darkTheme) BlueskyDarkColorScheme else BlueskyLightColorScheme
            AppTheme.Witchsky -> if (darkTheme) WitchskyDarkColorScheme else WitchskyLightColorScheme
            AppTheme.Blacksky -> if (darkTheme) BlackskyDarkColorScheme else BlackskyLightColorScheme
            AppTheme.Deer -> if (darkTheme) DeerDarkColorScheme else DeerLightColorScheme
            AppTheme.Zeppelin -> if (darkTheme) ZeppelinDarkColorScheme else ZeppelinLightColorScheme
            AppTheme.Kitty -> if (darkTheme) KittyDarkColorScheme else KittyLightColorScheme
            AppTheme.Reddwarf -> if (darkTheme) ReddwarfDarkColorScheme else ReddwarfLightColorScheme
            AppTheme.Catppuccin -> if (darkTheme) CatppuccinDarkColorScheme else CatppuccinLightColorScheme
            AppTheme.Evergarden -> if (darkTheme) EvergardenDarkColorScheme else EvergardenLightColorScheme
            AppTheme.BlueHeeler -> if (darkTheme) BlueHeelerDarkColorScheme else BlueHeelerLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
