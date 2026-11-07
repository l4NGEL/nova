package com.l4ngel.nova.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoDrinks
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Marka paleti: derin çivit + turuncu vurgu (seri alevi). Dinamik renk kapalı ki kimlik her cihazda aynı kalsın.
private val Light = lightColorScheme(
    primary = Color(0xFF4E54C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF0B0E5C),
    secondary = Color(0xFF5C5D72),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFFE8772E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC5),
    onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFF7F7FB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F1F7),
    surfaceContainer = Color(0xFFEBEBF2),
    surfaceContainerHigh = Color(0xFFE5E5EC),
    surfaceContainerHighest = Color(0xFFDFDFE6),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outlineVariant = Color(0xFFC7C5D0),
    inversePrimary = Color(0xFFBFC2FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBFC2FF),
    onPrimary = Color(0xFF1A1F7A),
    primaryContainer = Color(0xFF353A9E),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFC5C4DD),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFFFB783),
    onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFF6E3A0C),
    onTertiaryContainer = Color(0xFFFFDCC5),
    background = Color(0xFF111218),
    surface = Color(0xFF111218),
    // Kartlar bu rengi kullanıyor; koyu temada zeminden açık olmalı ki çukur gibi durmasın.
    surfaceContainerLowest = Color(0xFF1C1D25),
    surfaceContainerLow = Color(0xFF191A21),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerHigh = Color(0xFF282931),
    surfaceContainerHighest = Color(0xFF33343C),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outlineVariant = Color(0xFF46464F),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun NovaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

val TR: Locale = Locale("tr", "TR")
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM EEEE", TR)
private val MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", TR)

fun LocalDate.longLabel(): String = format(LONG_DATE)
fun LocalDate.monthLabel(): String = format(MONTH).replaceFirstChar { it.titlecase(TR) }

/** Alışkanlık renkleri: iki temada da beyaz simgeyle okunur doygunlukta. */
val HABIT_COLORS: List<Int> = listOf(
    0xFF5B6CF0, 0xFF2BA6DE, 0xFF00A6A6, 0xFF19A974, 0xFF7CB342, 0xFFF5A623,
    0xFFF0743E, 0xFFE5484D, 0xFFE0559B, 0xFF9B5DE5, 0xFF8D6E63, 0xFF607D8B,
).map { it.toInt() }

/** Veritabanında anahtar tutulur; simge seti değişse de eski kayıtlar bozulmaz. */
val HABIT_ICONS: List<Pair<String, ImageVector>> = listOf(
    "star" to Icons.Rounded.Star,
    "water" to Icons.Rounded.WaterDrop,
    "book" to Icons.Rounded.MenuBook,
    "walk" to Icons.Rounded.DirectionsWalk,
    "run" to Icons.Rounded.DirectionsRun,
    "bike" to Icons.Rounded.DirectionsBike,
    "fitness" to Icons.Rounded.FitnessCenter,
    "swim" to Icons.Rounded.Pool,
    "meditate" to Icons.Rounded.SelfImprovement,
    "mind" to Icons.Rounded.Psychology,
    "sleep" to Icons.Rounded.Bedtime,
    "sun" to Icons.Rounded.WbSunny,
    "food" to Icons.Rounded.Restaurant,
    "pill" to Icons.Rounded.Medication,
    "nosmoke" to Icons.Rounded.SmokeFree,
    "nodrink" to Icons.Rounded.NoDrinks,
    "study" to Icons.Rounded.School,
    "language" to Icons.Rounded.Translate,
    "code" to Icons.Rounded.Code,
    "write" to Icons.Rounded.Edit,
    "music" to Icons.Rounded.MusicNote,
    "art" to Icons.Rounded.Brush,
    "idea" to Icons.Rounded.Lightbulb,
    "money" to Icons.Rounded.Savings,
    "clean" to Icons.Rounded.CleaningServices,
    "plant" to Icons.Rounded.LocalFlorist,
    "nature" to Icons.Rounded.Park,
    "pet" to Icons.Rounded.Pets,
    "heart" to Icons.Rounded.Favorite,
)

private val ICON_BY_KEY = HABIT_ICONS.toMap()
fun habitIcon(key: String): ImageVector = ICON_BY_KEY[key] ?: Icons.Rounded.Star
