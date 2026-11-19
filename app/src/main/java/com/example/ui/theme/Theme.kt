package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val XChatDarkColorScheme = darkColorScheme(
    primary = XCrimson,
    onPrimary = MoonWhite,
    primaryContainer = DeepVelvetRed,
    onPrimaryContainer = RoseWhite,
    secondary = ElectricScarlet,
    onSecondary = ObsidianBlack,
    secondaryContainer = ElevatedOnyx,
    onSecondaryContainer = MoonWhite,
    tertiary = AmberCrown,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = MoonWhite,
    surface = CharcoalSurface,
    onSurface = MoonWhite,
    surfaceVariant = SmokedOnyx,
    onSurfaceVariant = CoolAsh,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = SmokedOnyx,
    surfaceContainerHigh = ElevatedOnyx,
    outline = HairlineSlate,
    outlineVariant = HairlineSlate,
    error = CoralAlert,
    onError = ObsidianBlack
)

val XChatShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun XChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = XChatDarkColorScheme,
        typography = XChatTypography,
        shapes = XChatShapes,
        content = content
    )
}
