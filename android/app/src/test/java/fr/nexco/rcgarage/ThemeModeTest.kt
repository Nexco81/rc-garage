package fr.nexco.rcgarage

import fr.nexco.rcgarage.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun systemFollowsDeviceAppearance() {
        assertFalse(ThemeMode.SYSTEM.usesDarkColors(false))
        assertTrue(ThemeMode.SYSTEM.usesDarkColors(true))
    }

    @Test fun explicitChoiceOverridesDeviceAppearance() {
        assertFalse(ThemeMode.LIGHT.usesDarkColors(true))
        assertFalse(ThemeMode.LIGHT.usesDarkColors(false))
        assertTrue(ThemeMode.DARK.usesDarkColors(false))
        assertTrue(ThemeMode.DARK.usesDarkColors(true))
    }
}
