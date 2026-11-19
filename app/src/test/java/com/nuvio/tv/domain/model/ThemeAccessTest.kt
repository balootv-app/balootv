package com.nuvio.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAccessTest {
    @Test
    fun standardAndCustomThemesAreKept() {
        assertEquals(AppTheme.CUSTOM, resolveAppTheme(AppTheme.CUSTOM))
        assertEquals(AppTheme.OCEAN, resolveAppTheme(AppTheme.OCEAN))
        assertEquals(AppTheme.WHITE, resolveAppTheme(null))
    }

    @Test
    fun supporterThemesFallBackToWhite() {
        listOf(AppTheme.GOLD, AppTheme.JADE, AppTheme.ROSE_GOLD, AppTheme.ARCTIC_BLUE, AppTheme.GRAPHITE)
            .forEach { assertEquals(AppTheme.WHITE, resolveAppTheme(it)) }
    }

    @Test
    fun customColorsAreSolid() {
        val gradient = CustomThemeColors(0xFF0000, 0x00FF00, 0x0000FF)
        val solid = CustomThemeColors.solid(0x00FF00)

        assertEquals(solid, resolveCustomThemeColors(gradient))
        assertEquals(solid, resolveCustomThemeColors(solid))
    }
}
