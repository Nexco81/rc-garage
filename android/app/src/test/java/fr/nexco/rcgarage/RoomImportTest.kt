package fr.nexco.rcgarage

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import fr.nexco.rcgarage.data.*
import fr.nexco.rcgarage.importer.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class RoomImportTest {
    private lateinit var db: GarageDatabase
    private lateinit var dao: GarageDao
    private lateinit var repo: GarageRepository
    private fun fixture() = javaClass.getResourceAsStream("/legacy-anonymized.json")!!.use { it.readBytes() }
    private fun json() = JSONObject(fixture().toString(Charsets.UTF_8))
    private fun preview(o: JSONObject) = LegacyImporter.preview(o.toString().toByteArray())
    private fun vid(i: Int) = LegacyImporter.stableId("vehicle","m$i")
    private fun eid(i: Int) = LegacyImporter.stableId("equipment","g$i")
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),GarageDatabase::class.java)
            .addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        dao=db.garage();repo=GarageRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun expectFailure(block: suspend ()->Unit) {
        var failed=false
        try { block() } catch(_: Exception) { failed=true }
        assertTrue("Expected an integrity failure",failed)
    }
    @Test fun previewDoesNotWriteAndConfirmationPreservesExactOriginal() = runBlocking {
        val original=fixture()
        val p=LegacyImporter.preview(original)
        assertTrue(p.errors.toString(),p.canConfirm)
        assertEquals(25,p.vehicleCount);assertEquals(6,p.equipmentCount);assertEquals(1,p.bodyCount)
        assertTrue(dao.vehicles().isEmpty());assertEquals(0,dao.sourceCount())
        original[0]=0 // Preview owns a copy, never a mutable caller buffer.
        assertTrue(repo.confirm(p) is ImportOutcome.Success)
        assertEquals(25,dao.vehicles().size);assertEquals(6,dao.equipment().size);assertEquals(1,dao.bodies().size)
        assertArrayEquals(fixture(),dao.source(p.sha256)!!.original)
        assertEquals(2,repo.assignedVehicles(eid(0)).first().size)
        assertEquals(0,repo.availableQuantity(eid(3)))
        assertEquals(VehicleStatus.TO_VERIFY,dao.vehicles().single{it.id==vid(24)}.status)
        val sale=dao.sales().single();assertFalse(sale.historyKnown);assertEquals(SaleType.UNKNOWN,sale.saleType)
        val senton=dao.vehicles().single{it.id==vid(24)}
        assertEquals(true,senton.isRtr);assertNull(senton.originalElectronicsJson)
    }
    @Test fun exactAndReformattedReimportsAreIdempotentAndNeverOverwrite() = runBlocking {
        val p=LegacyImporter.preview(fixture());assertTrue(repo.confirm(p) is ImportOutcome.Success)
        val second=repo.confirm(p) as ImportOutcome.Success;assertTrue(second.alreadyImported)
        val formatted=preview(json());assertNotEquals(p.sha256,formatted.sha256)
        val result=repo.confirm(formatted) as ImportOutcome.Success
        assertEquals(0,result.vehicles);assertEquals(0,result.equipment);assertEquals(0,result.bodies)
        assertEquals(25,dao.vehicles().size);assertEquals(2,dao.sourceCount())
        val changed=json();changed.getJSONArray("models").getJSONObject(0).put("model","Autre nom")
        assertTrue(repo.confirm(preview(changed)) is ImportOutcome.Failure)
        assertEquals(2,dao.sourceCount());assertFalse(dao.vehicles().any { it.name=="Autre nom" })
    }
    @Test fun invalidReferencesQuantitiesTypesAndIdsAreReportedBeforeWriting() = runBlocking {
        val cases=listOf<(JSONObject)->Unit>(
            {it.getJSONArray("models").getJSONObject(0).put("motor","missing")},
            {it.getJSONArray("gear").getJSONObject(0).put("quantity",-1)},
            {it.getJSONArray("gear").getJSONObject(0).put("quantity",1.5)},
            {it.getJSONArray("models").getJSONObject(0).put("brand","")},
            {it.getJSONArray("models").getJSONObject(0).put("rtr","true")},
            {it.getJSONArray("models").getJSONObject(0).put("servo","g3")},
            {it.getJSONArray("models").getJSONObject(1).put("memory","1")},
            {it.getJSONArray("models").getJSONObject(1).put("bodyIds",org.json.JSONArray().put("b0"))},
            {it.getJSONArray("models").put(JSONObject(it.getJSONArray("models").getJSONObject(0).toString()).put("model","Conflit"))},
        )
        for (change in cases) {
            val data=json();change(data);val p=preview(data)
            assertFalse(data.toString(),p.canConfirm);assertTrue(repo.confirm(p) is ImportOutcome.Failure)
        }
        assertEquals(0,dao.sourceCount());assertTrue(dao.vehicles().isEmpty())
        assertFalse(LegacyImporter.preview("{} garbage".toByteArray()).canConfirm)
        assertFalse(LegacyImporter.preview(byteArrayOf(0xc3.toByte(),0x28)).canConfirm)
        assertFalse(LegacyImporter.preview(ByteArray(LegacyImporter.MAX_BYTES+1)).canConfirm)
    }
    @Test fun missingFieldsRemainUnknownAndExplicitRtrOriginalIsPreserved() = runBlocking {
        val o=json();val m=o.getJSONArray("models").getJSONObject(24)
        m.put("originalElectronics",JSONObject().put("motor","Référence attestée par la source"))
        m.put("rtrOverride",true)
        o.getJSONArray("models").getJSONObject(3).remove("rtr")
        o.getJSONArray("models").getJSONObject(3).put("status","statut non documenté")
        val p=preview(o);assertTrue(p.canConfirm);repo.confirm(p)
        val v=dao.vehicles().single{it.id==vid(24)}
        assertTrue(v.originalElectronicsJson!!.contains("Référence attestée"));assertEquals(true,v.electronicsCustomized)
        val unknown=dao.vehicles().single{it.id==vid(3)}
        assertNull(unknown.isRtr);assertEquals(VehicleStatus.UNKNOWN,unknown.status)
        assertEquals("statut non documenté",unknown.legacyStatus)
        assertEquals(6,dao.equipment().size) // No artificial RTR equipment.
    }
    @Test fun unknownQuantityDoesNotBecomeOne() = runBlocking {
        val o=json();o.getJSONArray("gear").getJSONObject(5).remove("quantity")
        o.getJSONArray("models").getJSONObject(1).remove("motor")
        val p=preview(o);assertTrue(p.canConfirm);assertTrue(p.warnings.isNotEmpty());repo.confirm(p)
        assertNull(repo.availableQuantity(eid(5)))
        expectFailure { repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(2),eid(5),EquipmentType.MOTOR)) }
    }
    @Test fun overAllocationAndStockReductionFailAtDatabaseLevelAndReleasePreservesHistory() = runBlocking {
        repo.confirm(LegacyImporter.preview(fixture()))
        expectFailure { repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(2),eid(3),EquipmentType.MOTOR)) }
        expectFailure { repo.setOwnedQuantity(eid(3),0) }
        expectFailure { repo.setOwnedQuantity(eid(3),-1) }
        // A radio is shared, never consumed once per vehicle.
        repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(2),eid(0),EquipmentType.RADIO))
        assertEquals(3,repo.assignedVehicles(eid(0)).first().size)
        assertEquals(1,repo.availableQuantity(eid(0)))
        val a=dao.assignments().single{it.equipmentId==eid(3)}
        repo.release(a.id);assertEquals(1,repo.availableQuantity(eid(3)))
        repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(2),eid(3),EquipmentType.MOTOR))
        val history=repo.assignmentHistory(eid(3)).first()
        assertEquals(2,history.size);assertEquals(1,history.count{!it.isActive})
        expectFailure { db.openHelper.writableDatabase.execSQL("UPDATE assignments SET isActive=1 WHERE id='${a.id}'") }
        expectFailure { db.openHelper.writableDatabase.execSQL("DELETE FROM assignments") }
    }
    @Test fun importRollsBackAllRowsOnConflictWithAlreadyAllocatedStock() = runBlocking {
        // Existing vehicle owns a motor also referenced by the incoming file.
        val p=LegacyImporter.preview(fixture())
        val motor=p.plan.equipment.single{it.id==eid(3)}
        val existing=Vehicle(UUID.randomUUID(),"Existant","Véhicule")
        dao.insertVehicle(existing);dao.insertEquipment(motor)
        dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),existing.id,motor.id,EquipmentType.MOTOR))
        val result=repo.confirm(p);assertTrue(result is ImportOutcome.Failure)
        assertEquals(listOf(existing),dao.vehicles());assertEquals(listOf(motor),dao.equipment())
        assertTrue(dao.bodies().isEmpty());assertTrue(dao.sales().isEmpty());assertEquals(0,dao.sourceCount())
    }
    @Test fun foreignKeysUnitsAndImmutableJournalsProtectDirectDaoCallers() = runBlocking {
        repo.confirm(LegacyImporter.preview(fixture()))
        expectFailure { dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),UUID.randomUUID(),eid(0),EquipmentType.RADIO)) }
        val unit=EquipmentUnit(UUID.randomUUID(),eid(3),serial="ANON-1");dao.insertUnit(unit)
        expectFailure { dao.insertUnit(EquipmentUnit(UUID.randomUUID(),eid(3))) }
        expectFailure { dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),vid(2),eid(5),EquipmentType.MOTOR,unitId=unit.id)) }
        expectFailure { db.openHelper.writableDatabase.execSQL("DELETE FROM import_sources") }
        expectFailure { db.openHelper.writableDatabase.execSQL("UPDATE sales SET historyKnown=1") }
        expectFailure { dao.insertMemory(RadioMemory(UUID.randomUUID(),eid(3),10,vid(0))) }
        expectFailure { dao.insertBattery(Battery(UUID.randomUUID(),ownedQuantity=-1)) }
        expectFailure { db.withTransaction {
            dao.insertVehicle(Vehicle(UUID.randomUUID(),"Test","Rollback"))
            dao.insertSaleLine(SaleLine(UUID.randomUUID(),UUID.randomUUID(),equipmentSnapshotJson="{}",role=EquipmentType.MOTOR,disposition=Disposition.UNKNOWN,quantity=1))
        } }
        assertEquals(25,dao.vehicles().size)
    }
    @Test fun originalAndInlineBodiesAndUuidIdsSurviveImport() = runBlocking {
        val o=json();val id=UUID.randomUUID()
        o.getJSONArray("models").getJSONObject(2).put("id",id.toString())
            .put("boxBody",JSONObject().put("enabled",true).put("model","De boîte"))
            .put("bodies",org.json.JSONArray().put("Ancienne carrosserie"))
        assertTrue(repo.confirm(preview(o)) is ImportOutcome.Success)
        assertTrue(dao.vehicles().any{it.id==id})
        val original=dao.bodies().single{it.isOriginal==true}
        assertEquals(id,original.originVehicleId);assertEquals(id,original.currentVehicleId)
        assertEquals(3,dao.bodies().size)
        repo.moveBody(original.id,null)
        val detached=dao.bodies().single{it.id==original.id}
        assertNull(detached.currentVehicleId);assertEquals(id,detached.originVehicleId)
        expectFailure { db.openHelper.writableDatabase.execSQL("UPDATE body_shells SET originVehicleId=NULL WHERE id='${original.id}'") }
        repo.moveBody(original.id,vid(1))
        assertEquals(vid(1),dao.bodies().single{it.id==original.id}.currentVehicleId)
    }
    @Test fun identicalRowsAreDeduplicatedAndMultipleComponentsAreSupported() = runBlocking {
        val o=json();val models=o.getJSONArray("models")
        models.put(JSONObject(models.getJSONObject(0).toString()))
        val p=preview(o);assertTrue(p.canConfirm);assertEquals(25,p.vehicleCount)
        assertTrue(p.warnings.any{it.contains("doublon")})
        assertTrue(repo.confirm(p) is ImportOutcome.Success)
        val motor=Equipment(UUID.randomUUID(),EquipmentType.MOTOR,reference="Double moteur",ownedQuantity=3)
        dao.insertEquipment(motor)
        repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(0),motor.id,EquipmentType.MOTOR,quantity=2))
        repo.assign(EquipmentAssignment(UUID.randomUUID(),vid(0),motor.id,EquipmentType.MOTOR))
        assertEquals(0,repo.availableQuantity(motor.id))
        assertEquals(2,repo.assignmentHistory(motor.id).first().size)
        assertEquals(1,repo.assignedVehicles(motor.id).first().size)
    }
    @Test fun receiverRadioLinksAreValidatedAndCannotBeBrokenByReleasingTheRadio() = runBlocking {
        val o=json();o.getJSONArray("gear").getJSONObject(1).put("radioId","g0")
        assertTrue(repo.confirm(preview(o)) is ImportOutcome.Success)
        val radio=dao.assignments().single{it.vehicleId==vid(0) && it.role==EquipmentType.RADIO}
        val receiver=dao.assignments().single{it.vehicleId==vid(0) && it.role==EquipmentType.RECEIVER}
        expectFailure { repo.release(radio.id) }
        repo.release(receiver.id);repo.release(radio.id)
        val invalid=json();invalid.getJSONArray("gear").getJSONObject(1).put("radioId","missing")
        assertFalse(preview(invalid).canConfirm)
    }
    @Test fun hostileNestingIsRejectedButBracketsInsideNotesAreSafe() {
        assertFalse(LegacyImporter.preview(("[".repeat(100)+"]".repeat(100)).toByteArray()).canConfirm)
        val o=json();o.getJSONArray("models").getJSONObject(0).put("notes","[".repeat(100)+"\" texte")
        assertTrue(preview(o).canConfirm)
    }

}
