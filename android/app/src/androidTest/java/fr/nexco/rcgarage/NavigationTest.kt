package fr.nexco.rcgarage

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeNavigatesToAppearanceAndBack() {
        compose.onNodeWithText("Bienvenue dans RC Garage").assertIsDisplayed()
        compose.onNodeWithText("Apparence").performClick()
        compose.onNodeWithText("Sombre").performClick()
        compose.onNodeWithText("Sombre").onParent().assertIsSelected()
        compose.onNodeWithText("Retour à l’accueil").performClick()
        compose.onNodeWithText("Bienvenue dans RC Garage").assertIsDisplayed()
    }
}
