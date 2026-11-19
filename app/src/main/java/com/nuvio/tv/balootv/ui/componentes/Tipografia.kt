package com.nuvio.tv.balootv.ui.componentes

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import com.nuvio.tv.ui.theme.InterDisplayFamily
import com.nuvio.tv.ui.theme.InterFamily

/**
 * La tipografía de tvOS: SF Pro con tamaño óptico y espaciado que dependen del tamaño de cada texto, como hace
 * el sistema de Apple (en tvOS 1 punto = 1 píxel a 1080p; aquí 1 sp = 2 px).
 *
 * SF Pro no entra en Git: `scripts/balootv/fuentes_apple.py` deja una versión recortada en
 * `%USERPROFILE%\.balootv\assets\fuentes\sf_pro.ttf` y Gradle la añade a los assets. Sin ella, Inter.
 */
object Tipografia {

    const val RUTA_SF = "fuentes/sf_pro.ttf"

    /** Tabla `trak` de SF-Pro.ttf (pista 0): tamaño en puntos → espaciado en milésimas de em. */
    private val TRAK = listOf(
        6f to 40.0f, 9f to 18.6f, 10f to 11.7f, 11f to 5.9f, 12f to 0f, 13f to -5.9f, 14f to -10.7f, 15f to -15.6f,
        16f to -19.5f, 17f to -25.4f, 20f to -22.5f, 22f to -11.7f, 24f to 2.9f, 28f to 13.7f, 32f to 12.7f,
        36f to 10.3f, 50f to 6.8f, 64f to 3.4f, 80f to 0f,
    )

    /** Tramos del eje opsz de SF Pro (17 = Text, 28 = Display): limitan cuántas variantes se crean. */
    private val TRAMOS = listOf(17, 20, 22, 24, 26, 28)

    private val PESOS = listOf(400, 500, 600, 700, 800)

    /** Espaciado (fracción de em) que Apple aplica a un texto de [px] píxeles. */
    fun espaciadoEm(px: Float): Float {
        if (px <= TRAK.first().first) return TRAK.first().second / 1000f
        if (px >= TRAK.last().first) return TRAK.last().second / 1000f
        val i = TRAK.indexOfFirst { it.first >= px }
        val (x1, y1) = TRAK[i - 1]
        val (x2, y2) = TRAK[i]
        return (y1 + (y2 - y1) * (px - x1) / (x2 - x1)) / 1000f
    }

    /** Tamaño óptico para [px] píxeles: el tamaño mismo, entre 17 y 28, redondeado hacia abajo a un tramo. */
    fun tamanoOptico(px: Float): Int = TRAMOS.lastOrNull { it <= px } ?: TRAMOS.first()

    private var hayApple: Boolean? = null
    private val familias = HashMap<Int, FontFamily>()

    fun hayFuenteApple(assets: AssetManager): Boolean =
        hayApple ?: runCatching { assets.list("fuentes")?.contains("sf_pro.ttf") == true }.getOrDefault(false).also { hayApple = it }

    /** Familia para textos de [px] píxeles: SF Pro con su tamaño óptico o, sin ella, Inter (Display desde 26 px). */
    fun familia(assets: AssetManager, px: Float): FontFamily {
        val opsz = tamanoOptico(px)
        if (!hayFuenteApple(assets)) return if (opsz >= 26) InterDisplayFamily else InterFamily
        return familias.getOrPut(opsz) {
            FontFamily(PESOS.map { peso ->
                Font(RUTA_SF, assets, FontWeight(peso), variationSettings = FontVariation.Settings(
                    FontVariation.weight(peso), FontVariation.Setting("opsz", opsz.toFloat()),
                ))
            })
        }
    }
}

/**
 * Escala de texto de tvOS (Human Interface Guidelines): en tvOS 1 punto es 1 píxel a 1080p; aquí 1 sp = 2 px.
 * Por defecto todo va en peso Medium y lo resaltado en Bold, como en tvOS. Nada baja de [PIE_2] (23 px).
 */
object EscalaTv {
    val TITULO_1 = 38.sp   // 76 px
    val TITULO_2 = 28.5.sp // 57 px
    val TITULO_3 = 24.sp   // 48 px
    val TITULAR = 19.sp    // 38 px (Headline)
    val LLAMADA = 15.5.sp  // 31 px (Callout)
    val CUERPO = 14.5.sp   // 29 px (Body)
    val PIE_1 = 12.5.sp    // 25 px (Caption 1)
    val PIE_2 = 11.5.sp    // 23 px (Caption 2), el mínimo de tvOS

    /** Interlineado de cada tamaño de la escala, en px. */
    private val INTERLINEADO = mapOf(76 to 96, 57 to 66, 48 to 56, 38 to 46, 31 to 38, 29 to 36, 25 to 32, 23 to 30)

    /** Interlineado en px para un texto de [px] píxeles, o null si no es un tamaño de la escala. */
    fun interlineado(px: Float): Float? = INTERLINEADO[Math.round(px)]?.toFloat()
}

/**
 * `Text` de BalooTV: el de tv-material con lo que tvOS hace solo: tamaño óptico, espaciado e interlineado según el
 * tamaño, y peso Medium si no se dice otro. Lo que se pase explícito manda sobre lo calculado.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val tamano = if (fontSize.isSpecified) fontSize else style.fontSize
    val densidad = LocalDensity.current
    val px = if (tamano.isSpecified) with(densidad) { tamano.toPx() } else 28f
    val interlineado = if (lineHeight.isSpecified) lineHeight else EscalaTv.interlineado(px)?.let { with(densidad) { it.toSp() } } ?: lineHeight
    androidx.tv.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight ?: FontWeight.Medium,
        fontFamily = fontFamily ?: Tipografia.familia(LocalContext.current.assets, px),
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing else Tipografia.espaciadoEm(px).em,
        textAlign = textAlign,
        lineHeight = interlineado,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}
