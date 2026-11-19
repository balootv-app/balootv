package com.nuvio.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import com.nuvio.tv.R

// BalooTV: Inter es la reserva de SF Pro (que no está en Git). Fuente variable con su peso explícito; sin esto todo
// se ve en peso normal. Inter 4 tiene tamaño óptico (eje opsz, 14–32), como SF Pro Text/Display en tvOS. En la tele 1 sp son
// 2 px y casi todo el texto mide 20–30 px: 24 le va mejor que el 14 por defecto (pensado para letra de 14 px).
// Los titulares usan el corte Display (32): más apretado y con más contraste, como los de Apple.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(peso: Int, opsz: Float) = Font(
    R.font.inter_variable, FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso), FontVariation.Setting("opsz", opsz)),
)

val InterFamily = FontFamily(listOf(400, 500, 600, 700, 800).map { inter(it, 24f) })

/** Inter Display para titulares (16 sp o más). */
val InterDisplayFamily = FontFamily(listOf(500, 600, 700, 800).map { inter(it, 32f) })

@Immutable
data class NuvioTextStyleTokens(
    val display: TextStyle,
    val displayCompact: TextStyle,
    val headline: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyCompact: TextStyle,
    val metadata: TextStyle,
    val badge: TextStyle,
    val button: TextStyle,
    val tab: TextStyle,
    val nav: TextStyle,
    val playerControl: TextStyle
)

@OptIn(ExperimentalTvMaterial3Api::class)
fun buildNuvioTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
val NuvioTypography = buildNuvioTypography(InterFamily)

@OptIn(ExperimentalTvMaterial3Api::class)
fun buildNuvioTextStyles(typography: Typography): NuvioTextStyleTokens = NuvioTextStyleTokens(
    display = typography.displayLarge,
    displayCompact = typography.displayMedium,
    headline = typography.headlineLarge,
    sectionTitle = typography.headlineMedium,
    cardTitle = typography.titleMedium,
    body = typography.bodyLarge,
    bodyCompact = typography.bodyMedium,
    metadata = typography.labelMedium,
    badge = typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    ),
    button = typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    tab = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    nav = typography.titleMedium,
    playerControl = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
)

@OptIn(ExperimentalTvMaterial3Api::class)
val NuvioTextStyles = buildNuvioTextStyles(NuvioTypography)
