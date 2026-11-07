package fr.nexco.rcgarage.ui.theme

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun usesDarkColors(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}
