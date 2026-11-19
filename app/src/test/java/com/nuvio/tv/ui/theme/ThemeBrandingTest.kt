package com.nuvio.tv.ui.theme

import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeBrandingTest {
    @Test
    fun todosLosTemasUsanElLogoDeBalooTV() {
        AppTheme.entries.forEach { theme ->
            assertEquals(R.drawable.balootv_logo, theme.brandWordmarkResource)
        }
    }
}
