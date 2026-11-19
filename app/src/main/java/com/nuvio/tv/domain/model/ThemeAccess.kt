package com.nuvio.tv.domain.model

// Los temas de socio de Nuvio quedan fuera: sin cuenta nunca se podían aplicar.
private val supporterThemes = setOf(
    AppTheme.GOLD,
    AppTheme.JADE,
    AppTheme.ROSE_GOLD,
    AppTheme.ARCTIC_BLUE,
    AppTheme.GRAPHITE
)

fun resolveAppTheme(selectedTheme: AppTheme?): AppTheme =
    selectedTheme?.takeIf { it !in supporterThemes } ?: AppTheme.WHITE

fun resolveCustomThemeColors(colors: CustomThemeColors): CustomThemeColors =
    CustomThemeColors.solid(colors.second)
