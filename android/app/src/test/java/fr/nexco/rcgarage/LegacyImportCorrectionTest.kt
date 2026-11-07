package fr.nexco.rcgarage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.nexco.rcgarage.data.*
import fr.nexco.rcgarage.importer.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class LegacyImportCorrectionTest {
    private lateinit var db: GarageDatabase
    private lateinit var repo: GarageRepository
    private val dao get()=db.garage()
    private fun fixture()=JSONObject(javaClass.getResourceAsStream("/legacy-anonymized.json")!!.bufferedReader().use{it.readText()})
    private fun preview(o: JSONObject)=LegacyImporter.preview(o.toString().toByteArray())
    private fun eid(i: Int)=LegacyImporter.stableId("equipment","g$i")
    private fun vid(i: Int)=LegacyImporter.stableId("vehicle","m$i")
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),GarageDatabase::class.java)
            .addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        repo=GarageRepository(db)
    }
    @After fun close()=db.close()

    private fun sharedG7P(): JSONObject = fixture().apply {
        getJSONArray("gear").getJSONObject(0).put("name","Flysky G7P").remove("quantity")
        val models=getJSONArray("models")
        for(i in 0..2) models.getJSONObject(i).put("radio","g0").put("memory",(i+1).toString())
    }

    @Test fun g7pSharesThreeModelMemoriesWithoutInventingThreePhysicalRadios() = runBlocking {
        val o=sharedG7P();val p=preview(o)
        assertTrue(p.errors.toString(),p.canConfirm)
        assertTrue(p.warnings.any{it.contains("quantité de radio inconnue")})
        assertTrue(dao.vehicles().isEmpty()) // Preview and abandoning it never write.
        assertTrue(repo.confirm(p) is ImportOutcome.Success)
        val g7p=dao.equipment().single{it.id==eid(0)}
        assertEquals("Flysky G7P",g7p.reference);assertNull(g7p.ownedQuantity)
        assertNull(repo.availableQuantity(g7p.id))
        assertEquals(setOf(vid(0),vid(1),vid(2)),repo.assignedVehicles(g7p.id).first().map{it.id}.toSet())
        assertEquals(setOf(1,2,3),dao.memories().filter{it.radioId==g7p.id}.map{it.slot}.toSet())
        // Explicit physical quantities and associations are independent, even at zero.
        repo.setOwnedQuantity(g7p.id,0);assertEquals(0,repo.availableQuantity(g7p.id))
        repo.setOwnedQuantity(g7p.id,1);assertEquals(1,repo.availableQuantity(g7p.id))
        repo.setOwnedQuantity(g7p.id,null);assertNull(repo.availableQuantity(g7p.id))
        assertEquals(3,repo.assignedVehicles(g7p.id).first().size)
        assertEquals(3,dao.memories().filter{it.radioId==g7p.id}.size)
        assertArrayEquals(p.originalBytes(),dao.source(p.sha256)!!.original)
        assertTrue((repo.confirm(p) as ImportOutcome.Success).alreadyImported)
    }

    @Test fun gt5AlsoKeepsMissingQuantityUnknownAndCanBeShared() = runBlocking {
        val o=sharedG7P();o.getJSONArray("gear").getJSONObject(0).put("name","Flysky GT5")
        assertTrue(repo.confirm(preview(o)) is ImportOutcome.Success)
        assertNull(dao.equipment().single{it.id==eid(0)}.ownedQuantity)
        assertEquals(3,repo.assignedVehicles(eid(0)).first().size)
    }

    @Test fun knownPhysicalRadioQuantityNeverDecreasesWhenModelsAreAssociated() = runBlocking {
        val o=sharedG7P();o.getJSONArray("gear").getJSONObject(0).put("quantity","1")
        assertTrue(repo.confirm(preview(o)) is ImportOutcome.Success)
        assertEquals(1,dao.equipment().single{it.id==eid(0)}.ownedQuantity)
        assertEquals(1,repo.availableQuantity(eid(0)))
        assertEquals(3,repo.assignedVehicles(eid(0)).first().size)
        repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(3),eid(0),EquipmentType.RADIO))
        assertEquals(1,repo.availableQuantity(eid(0)));assertEquals(4,repo.assignedVehicles(eid(0)).first().size)
    }

    @Test fun numericStringsAreValidatedConvertedAndDeduplicatedAgainstNumericValues() = runBlocking {
        val o=fixture();val gear=o.getJSONArray("gear")
        val values=listOf("1","4","10"," 01 ","2147483647","0")
        values.forEachIndexed { i,v -> gear.getJSONObject(i).put("quantity",v) }
        // g5 explicitly has no owned stock, so do not assign it in this synthetic fixture.
        o.getJSONArray("models").getJSONObject(1).remove("motor")
        val p=preview(o);assertTrue(p.errors.toString(),p.canConfirm)
        assertTrue(repo.confirm(p) is ImportOutcome.Success)
        values.map{it.trim().toInt()}.forEachIndexed { i,v -> assertEquals(v,dao.equipment().single{it.id==eid(i)}.ownedQuantity) }
        val before=dao.equipment()
        values.forEachIndexed { i,v -> gear.getJSONObject(i).put("quantity",v.trim().toInt()) }
        val result=repo.confirm(preview(o)) as ImportOutcome.Success
        assertEquals(0,result.equipment);assertEquals(0,result.vehicles)
        assertEquals(before,dao.equipment()) // No rewrite of previously stored raw specifications.
        assertEquals(2,dao.sourceCount()) // Both distinct original byte streams are retained.
    }

    @Test fun malformedQuantitiesCannotBecomeZeroOrUnknownByAccident() = runBlocking {
        for(value in listOf("", " ","1.5","-1","+1","1e2","NaN","Infinity","2147483648","1x",true,1.5,-1,2147483648L)) {
            val o=fixture();o.getJSONArray("gear").getJSONObject(2).put("quantity",value)
            val p=preview(o);assertFalse("Accepted $value",p.canConfirm)
            assertTrue(repo.confirm(p) is ImportOutcome.Failure)
        }
        assertTrue(dao.vehicles().isEmpty());assertEquals(0,dao.sourceCount())
    }

    @Test fun boxBodiesAndIndependentStockKeepDistinctProvenanceAndPreviewCounts() = runBlocking {
        val o=fixture();val m=o.getJSONArray("models").getJSONObject(0)
        m.put("boxBody",JSONObject().put("enabled",true).put("brand","Tamiya").put("model","Carrosserie du kit"))
        o.getJSONArray("models").getJSONObject(1).put("boxBody",JSONObject().put("enabled",false).put("model","Non montée"))
        val p=preview(o)
        assertEquals(1,p.originalBodyCount);assertEquals(1,p.independentBodyCount);assertEquals(2,p.bodyCount)
        assertTrue(repo.confirm(p) is ImportOutcome.Success)
        val original=dao.bodies().single{it.isOriginal==true}
        val independent=dao.bodies().single{it.id==LegacyImporter.stableId("body","b0")}
        assertEquals(vid(0),original.originVehicleId);assertEquals(vid(0),original.currentVehicleId)
        assertEquals("Carrosserie du kit",original.name)
        assertNull(independent.originVehicleId);assertNull(independent.isOriginal)
        assertEquals(vid(0),independent.currentVehicleId)
        assertTrue((repo.confirm(p) as ImportOutcome.Success).alreadyImported)
        assertEquals(2,dao.bodies().size)
    }

    @Test fun radioFixPreservesUuidLinksStatusesNotesRtrAndUnknownSaleHistory() = runBlocking {
        val o=sharedG7P();val model=o.getJSONArray("models").getJSONObject(0)
        val uuid=UUID.randomUUID();model.put("id",uuid.toString()).put("notes","  Note historique\nDeuxième ligne\n  ")
            .put("status","Préparation compétition").put("rtr",true).put("rtrOverride",true)
            .put("originalElectronics",JSONObject().put("motor","Moteur explicitement documenté"))
        o.getJSONArray("bodyStock").getJSONObject(0).put("modelId",uuid.toString())
        val p=preview(o);assertTrue(repo.confirm(p) is ImportOutcome.Success)
        val imported=dao.vehicles().single{it.id==uuid}
        assertEquals(VehicleStatus.COMPETITION_PREP,imported.status)
        assertEquals("Préparation compétition",imported.legacyStatus)
        assertEquals(model.getString("notes"),imported.notes)
        assertEquals(true,imported.isRtr);assertEquals(true,imported.electronicsCustomized)
        assertEquals(model.getJSONObject("originalElectronics").toString(),imported.originalElectronicsJson)
        assertTrue(dao.assignments().any{it.vehicleId==uuid && it.equipmentId==eid(0)})
        assertEquals(uuid,dao.bodies().single().currentVehicleId)
        assertEquals(false,dao.sales().single().historyKnown);assertEquals(SaleType.UNKNOWN,dao.sales().single().saleType)
        assertArrayEquals(p.originalBytes(),dao.source(p.sha256)!!.original)
    }

    @Test fun unknownRadioDoesNotBypassNonRadioStockOrAtomicRollback() = runBlocking {
        val o=sharedG7P();val p=preview(o)
        val motor=p.plan.equipment.single{it.id==eid(3)}
        val existing=Vehicle(UUID.randomUUID(),"Test","Existant")
        dao.insertVehicle(existing);dao.insertEquipment(motor)
        dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),existing.id,motor.id,EquipmentType.MOTOR))
        assertTrue(repo.confirm(p) is ImportOutcome.Failure)
        assertEquals(listOf(existing),dao.vehicles());assertEquals(listOf(motor),dao.equipment())
        assertTrue(dao.bodies().isEmpty());assertTrue(dao.sales().isEmpty());assertEquals(0,dao.sourceCount())
        val invalid=sharedG7P();invalid.getJSONArray("gear").getJSONObject(3).remove("quantity")
        assertFalse(preview(invalid).canConfirm)
    }
    @Test fun explicitZeroPhysicalRadioQuantityIsNotInferredFromModelCount() = runBlocking {
        val o=sharedG7P();o.getJSONArray("gear").getJSONObject(0).put("quantity","0")
        assertTrue(repo.confirm(preview(o)) is ImportOutcome.Success)
        assertEquals(0,dao.equipment().single{it.id==eid(0)}.ownedQuantity)
        assertEquals(3,repo.assignedVehicles(eid(0)).first().size)
    }

}
