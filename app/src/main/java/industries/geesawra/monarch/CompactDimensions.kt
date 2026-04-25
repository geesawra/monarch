package industries.geesawra.monarch

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun isNarrowScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp <= 360

@Composable
fun isExpandedScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 840

@Composable
fun feedHorizontalPadding(): Dp = when {
    isNarrowScreen() -> 6.dp
    isExpandedScreen() -> 8.dp
    else -> 16.dp
}

@Composable
fun feedItemSpacing(): Dp = if (isNarrowScreen()) 8.dp else 16.dp

@Composable
fun postHorizontalPadding(): Dp = when {
    isNarrowScreen() -> 10.dp
    isExpandedScreen() -> 8.dp
    else -> 16.dp
}

@Composable
fun avatarSize(): Dp = when {
    isNarrowScreen() -> 32.dp
    isExpandedScreen() -> 48.dp
    else -> 40.dp
}

@Composable
fun avatarTextGap(): Dp = if (isNarrowScreen()) 8.dp else 12.dp

@Composable
fun actionIconSize(): Dp = if (isNarrowScreen()) 14.dp else 18.dp

@Composable
fun topBarAvatarSize(): Dp = if (isNarrowScreen()) 28.dp else 40.dp
