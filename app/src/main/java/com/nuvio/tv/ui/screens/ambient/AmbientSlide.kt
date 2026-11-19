package com.nuvio.tv.ui.screens.ambient

data class AmbientSlide(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String?,
    /** BalooTV: los géneros como en la ficha, «Acción, aventura»; null sin géneros. */
    val generos: String?
)
