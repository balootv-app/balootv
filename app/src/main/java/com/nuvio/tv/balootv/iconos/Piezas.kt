package com.nuvio.tv.balootv.iconos

/**
 * El vocabulario de iconos común con el móvil (propuesta del 31-10-2026, `docs/propuesta-iconos/`): cada uno significa
 * una sola cosa y es el mismo símbolo de Material Symbols Rounded en las dos apps. Los dibujos están en
 * `res/drawable/simbolo_*.xml`.
 */
enum class Simbolo {
    /** Escena durante o después de los créditos (hourglass_bottom). */
    ESCENA,
    /** Sin escena tras los créditos (hourglass_disabled). */
    SIN_ESCENA,
    /** «Termina a las» (sports_score). */
    TERMINA,
    /** Siguiente episodio (skip_next). */
    SIGUIENTE,
    /** Visto (check). */
    VISTO,
    /** Apuntar a Mi lista (bookmark_add). */
    APUNTAR,
    /** Ya en Mi lista (bookmark_added). */
    APUNTADA,
    /** Sale ese día: el episodio aún no se ha emitido (event). */
    SALE,
    /** Audio, siempre con el idioma en palabra (volume_up). */
    AUDIO,
    /** Subtítulos (subtitles). */
    SUBTITULOS,
    /** Fuente lista en la caché de TorBox (bolt). */
    LISTA,
    /** Fuente que TorBox tiene que bajar antes (download). */
    PREPARAR,
    /** La fuente que suena (graphic_eq). */
    REPRODUCIENDO,
    /** En pausa (pause). */
    PAUSA,
}

/** Un trozo de una línea de la interfaz: texto, un icono del vocabulario o una insignia («4K»). */
sealed interface Pieza {
    data class Texto(val texto: String) : Pieza
    /** [descripcion] es lo que el icono sustituye, para quien no lo vea. */
    data class Icono(val simbolo: Simbolo, val descripcion: String? = null) : Pieza
    data class Insignia(val texto: String) : Pieza
    /** El «·» entre dos datos. */
    data object Separador : Pieza
}

/** Une grupos de piezas con «·», saltándose los vacíos. */
fun conSeparadores(vararg grupos: List<Pieza>): List<Pieza> =
    grupos.filter { it.isNotEmpty() }.flatMapIndexed { i, g -> if (i == 0) g else listOf(Pieza.Separador) + g }
