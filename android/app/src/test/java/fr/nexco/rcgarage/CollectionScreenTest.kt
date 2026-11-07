package fr.nexco.rcgarage

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import fr.nexco.rcgarage.data.*
import fr.nexco.rcgarage.importer.LegacyImporter
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
@LooperMode(LooperMode.Mode.PAUSED)
class CollectionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun importedCollectionCanBeOpenedCancelledEditedAndBrowsedByServoCategory() {
        val db=GarageDatabase.get(ApplicationProvider.getApplicationContext())
        val bytes=javaClass.getResourceAsStream("/legacy-anonymized.json")!!.use{it.readBytes()}
        runBlocking { assertTrue(GarageRepository(db).confirm(LegacyImporter.preview(bytes)) is ImportOutcome.Success) }
        compose.onNodeWithText("Collection et stocks").performClick()
        compose.waitUntil(15_000) {compose.onAllNodesWithText("Collection — 25 modèles").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("field:Rechercher un modèle").performTextReplacement("Modèle 1")
        compose.onNodeWithTag("vehicles-list").performScrollToNode(hasText("Marque test Modèle 1"))
        compose.onNodeWithText("Marque test Modèle 1").performClick()
        compose.onNodeWithText("Modifier le modèle").performClick()
        compose.onNodeWithTag("field:Nom / référence").performTextReplacement("À annuler")
        compose.onNodeWithText("Annuler").performClick()
        runBlocking {assertEquals("Modèle 1",db.garage().vehicles().single{it.id==LegacyImporter.stableId("vehicle","m0")}.name)}
        compose.onNodeWithText("Modifier le modèle").performClick()
        compose.onNodeWithTag("field:Nom / référence").performTextReplacement("Nom sauvegardé")
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitUntil(15_000) {compose.onAllNodesWithText("Marque test Nom sauvegardé").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Stocks").performClick()
        compose.onNodeWithText("Catégorie : Toutes").performClick()
        compose.onNodeWithText("Servo de direction").performClick()
        compose.onNodeWithText("Référence test 5").performClick()
        compose.onNodeWithText("Modèles utilisateurs").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Marque test Nom sauvegardé") and hasClickAction()).performScrollTo().assertIsDisplayed()
        runBlocking {
            assertEquals(25,db.garage().vehicles().size)
            assertEquals(1,db.garage().sales().size)
            assertArrayEquals(bytes,db.garage().source(LegacyImporter.hash(bytes))!!.original)
        }
        compose.activityRule.scenario.close()
        db.close()
    }
}
