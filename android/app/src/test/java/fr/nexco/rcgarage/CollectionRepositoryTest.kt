package fr.nexco.rcgarage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.nexco.rcgarage.collection.*
import fr.nexco.rcgarage.data.*
import fr.nexco.rcgarage.importer.LegacyImporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class CollectionRepositoryTest {
    private lateinit var db: GarageDatabase
    private lateinit var repo: CollectionRepository
    private val dao get()=db.garage()
    private fun bytes()=javaClass.getResourceAsStream("/legacy-anonymized.json")!!.use{it.readBytes()}
    private fun vid(i: Int)=LegacyImporter.stableId("vehicle","m$i")
    private fun eid(i: Int)=LegacyImporter.stableId("equipment","g$i")
    private fun fields(v: Vehicle)=VehicleFields(v.brand,v.name,v.chassis,v.status,v.usage,v.notes,v.isRtr,v.electronicsCustomized)
    private fun inMemory()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),GarageDatabase::class.java)
        .addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
    @Before fun open()=runBlocking {
        db=inMemory();repo=CollectionRepository(db)
        assertTrue(GarageRepository(db).confirm(LegacyImporter.preview(bytes())) is ImportOutcome.Success)
    }
    @After fun close()=db.close()
    private suspend fun fails(block: suspend ()->Unit) {
        var failed=false
        try {block()} catch(_:Exception){failed=true}
        assertTrue("Expected failure without partial changes",failed)
    }
    @Test fun editsPreserveImportedIdsOtherRowsRawOriginalAndSaleJournal()=runBlocking {
        val before=repo.snapshot();val v=before.vehicles.single{it.id==vid(0)}
        repo.saveVehicle(v,v.id,fields(v).copy(brand="Marque libre",name="Nom modifié",usage="Piste",notes="  notes\nexactes  "))
        val after=repo.snapshot()
        assertEquals(25,after.vehicles.size)
        assertEquals(before.vehicles.map{it.id}.toSet(),after.vehicles.map{it.id}.toSet())
        assertEquals(before.vehicles.filter{it.id!=v.id},after.vehicles.filter{it.id!=v.id})
        val edited=after.vehicles.single{it.id==v.id}
        assertEquals("Nom modifié",edited.name);assertEquals("  notes\nexactes  ",edited.notes)
        assertEquals(before.assignments,after.assignments);assertEquals(before.bodies,after.bodies)
        assertEquals(before.sales,after.sales)
        val p=LegacyImporter.preview(bytes());assertArrayEquals(bytes(),dao.source(p.sha256)!!.original)
        assertTrue((GarageRepository(db).confirm(p) as ImportOutcome.Success).alreadyImported)
        assertEquals("Nom modifié",dao.vehicles().single{it.id==v.id}.name)
    }
    @Test fun brandFilteredChassisAndSearchNeverInventDefaults()=runBlocking {
        val a=Vehicle(UUID.randomUUID(),"Tamiya","Tourisme",chassis="TT-02")
        val b=Vehicle(UUID.randomUUID(),"Traxxas","Slash",chassis="Slash 4x4")
        dao.insertVehicle(a);dao.insertVehicle(b)
        val data=repo.snapshot()
        assertEquals(listOf("TT-02"),data.chassisSuggestions(" tamiya "))
        assertEquals(listOf("Slash 4x4"),data.chassisSuggestions("TRAXXAS"))
        assertTrue(data.chassisSuggestions("Marque nouvelle").isEmpty())
        assertEquals(b.id,data.filteredVehicles("slash",null).single().id)
        assertEquals(vid(24),data.filteredVehicles("SENTON",VehicleStatus.TO_VERIFY).single().id)
        assertEquals(1,data.filteredVehicles("",VehicleStatus.SOLD).size)
        assertTrue(data.filteredVehicles("introuvable",null).isEmpty())
    }
    @Test fun rtrVisibilityNeverDeletesStockAssignmentsMemoriesOrOriginalElectronics()=runBlocking {
        val seed=Vehicle(UUID.randomUUID(),"Test","RTR",isRtr=true,electronicsCustomized=true,
            originalElectronicsJson="{\"motor\":\"Origine documentée\"}")
        dao.insertVehicle(seed)
        repo.saveAssignment(seed.id,null,eid(0),1,3)
        val before=repo.snapshot()
        repo.saveVehicle(seed,seed.id,fields(seed).copy(electronicsCustomized=false))
        val hidden=repo.snapshot().vehicles.single{it.id==seed.id}
        assertTrue(hidden.originElectronicsVisible);assertEquals(true,hidden.isRtr)
        assertEquals(seed.originalElectronicsJson,hidden.originalElectronicsJson)
        val assignment=repo.snapshot().assignments.single{it.vehicleId==seed.id}
        fails {repo.removeAssignment(assignment)}
        fails {repo.saveAssignment(seed.id,assignment,eid(0),1,4)}
        repo.saveVehicle(hidden,hidden.id,fields(hidden).copy(electronicsCustomized=true))
        val restored=repo.snapshot()
        assertFalse(restored.vehicles.single{it.id==seed.id}.originElectronicsVisible)
        assertEquals(before.assignments,restored.assignments);assertEquals(before.memories,restored.memories)
        assertEquals(before.equipment,restored.equipment)
        val current=restored.vehicles.single{it.id==seed.id}
        repo.saveVehicle(current,current.id,fields(current).copy(isRtr=false))
        assertEquals(before.assignments,repo.snapshot().assignments)
        assertEquals(seed.originalElectronicsJson,repo.snapshot().vehicles.single{it.id==seed.id}.originalElectronicsJson)
    }
    @Test fun sharedRadioKeepsUnknownQuantityAndSupportsOptionalUniqueMemories()=runBlocking {
        val radio=dao.equipment().single{it.id==eid(0)}
        repo.saveEquipment(radio,radio.copy(ownedQuantity=null))
        repo.saveAssignment(vid(2),null,radio.id,1,3)
        val data=repo.snapshot();assertNull(data.available(data.equipment.single{it.id==radio.id}))
        assertEquals(3,data.users(radio.id).size)
        assertEquals(setOf(1,2,3),data.memories.filter{it.radioId==radio.id}.map{it.slot}.toSet())
        val before=repo.snapshot()
        fails {repo.saveAssignment(vid(3),null,radio.id,1,3)}
        assertEquals(before,repo.snapshot())
        repo.saveAssignment(vid(3),null,radio.id,1,null)
        assertEquals(4,repo.snapshot().users(radio.id).size)
        assertFalse(repo.snapshot().memories.any{it.vehicleId==vid(3)})
        val a=repo.snapshot().assignments.single{it.vehicleId==vid(2) && it.role==EquipmentType.RADIO}
        repo.removeAssignment(a)
        assertFalse(repo.snapshot().memories.any{it.vehicleId==vid(2)})
        assertEquals(3,repo.snapshot().users(radio.id).size)
        assertFalse(repo.snapshot().assignments.single{it.id==a.id}.isActive)
    }
    @Test fun servosUsePhysicalStockAndFailedReassignmentRollsBackRelease()=runBlocking {
        val servo=Equipment(UUID.randomUUID(),EquipmentType.SERVO,brand="Libre",reference="Servo B",ownedQuantity=2)
        repo.saveEquipment(null,servo)
        repo.saveAssignment(vid(2),null,servo.id,1,null)
        val old=dao.assignments().single{it.equipmentId==eid(4) && it.isActive}
        val before=repo.snapshot()
        fails {repo.saveAssignment(vid(0),old,servo.id,2,null)}
        assertEquals(before,repo.snapshot())
        repo.saveAssignment(vid(0),old,servo.id,1,null)
        val after=repo.snapshot()
        assertFalse(after.assignments.single{it.id==old.id}.isActive)
        assertEquals(0L,after.available(after.equipment.single{it.id==servo.id}))
        assertEquals(setOf(vid(0),vid(2)),after.users(servo.id).map{it.id}.toSet())
        assertEquals(1L,after.available(after.equipment.single{it.id==eid(4)}))
        val live=after.assignments.single{it.vehicleId==vid(0) && it.equipmentId==servo.id && it.isActive}
        repo.removeAssignment(live)
        assertEquals(1L,repo.snapshot().available(repo.snapshot().equipment.single{it.id==servo.id}))
        assertEquals(2,repo.snapshot().assignments.count{it.equipmentId==servo.id})
    }
    @Test fun insufficientUnknownStockAndStaleEditorsCannotOverwriteRows()=runBlocking {
        val e=dao.equipment().single{it.id==eid(4)}
        fails {repo.saveEquipment(e,e.copy(ownedQuantity=0))}
        fails {repo.saveEquipment(e,e.copy(ownedQuantity=null))}
        assertEquals(e,dao.equipment().single{it.id==e.id})
        repo.saveEquipment(e,e.copy(brand="Nouvelle marque",reference="Référence éditée"))
        fails {repo.saveEquipment(e,e.copy(reference="Éditeur périmé"))}
        assertEquals("Référence éditée",dao.equipment().single{it.id==e.id}.reference)
        assertEquals(e.specsJson,dao.equipment().single{it.id==e.id}.specsJson)
        val v=dao.vehicles().single{it.id==vid(0)}
        repo.saveVehicle(v,v.id,fields(v).copy(name="Premier"))
        fails {repo.saveVehicle(v,v.id,fields(v).copy(name="Écrasement"))}
        assertEquals("Premier",dao.vehicles().single{it.id==v.id}.name)
        val unknown=Equipment(UUID.randomUUID(),EquipmentType.ESC,reference="Inconnu")
        repo.saveEquipment(null,unknown)
        fails {repo.saveAssignment(vid(2),null,unknown.id,1,null)}
    }
    @Test fun originalBodiesCanDetachWithoutLosingProvenanceAndIndependentBodiesStayIndependent()=runBlocking {
        val original=BodyShell(UUID.randomUUID(),vid(0),vid(0),true,"Libre","De boîte")
        dao.insertBody(original)
        repo.saveBody(original,original.copy(currentVehicleId=null,notes="Retour au stock"))
        val detached=repo.snapshot().bodies.single{it.id==original.id}
        assertNull(detached.currentVehicleId);assertEquals(vid(0),detached.originVehicleId);assertEquals(true,detached.isOriginal)
        fails {repo.saveBody(detached,detached.copy(originVehicleId=vid(1)))}
        repo.saveBody(detached,detached.copy(currentVehicleId=vid(1)))
        val independent=repo.snapshot().bodies.single{it.id==LegacyImporter.stableId("body","b0")}
        repo.saveBody(independent,independent.copy(brand="Saisie libre",name="Carrosserie éditée"))
        assertNull(repo.snapshot().bodies.single{it.id==independent.id}.originVehicleId)
        assertEquals(2,repo.snapshot().bodies.size)
    }
    @Test fun batteryFieldsAndCompatibilityPersistWithoutInventingAllocation()=runBlocking {
        val battery=Battery(UUID.randomUUID(),brand="Libre",reference="Pack",chemistry="LiPo",capacityMah=5000,cells=2,
            voltage=7.4,cRating=50.0,connector="XT60")
        repo.saveBattery(null,battery,emptySet(),setOf(vid(0),vid(1)))
        val data=repo.snapshot();assertEquals(battery,data.batteries.single());assertNull(data.batteries.single().ownedQuantity)
        repo.saveBattery(battery,battery.copy(ownedQuantity=4),setOf(vid(0),vid(1)),setOf(vid(2)))
        assertEquals(4,repo.snapshot().batteries.single().ownedQuantity)
        assertEquals(listOf(BatteryCompatibility(battery.id,vid(2))),repo.snapshot().compatibility)
        val current=repo.snapshot().batteries.single()
        val before=repo.snapshot()
        fails {repo.saveBattery(current,current.copy(capacityMah=0),setOf(vid(2)),emptySet())}
        fails {repo.saveBattery(current,current.copy(voltage=Double.NaN),setOf(vid(2)),emptySet())}
        fails {repo.saveBattery(current,current,setOf(vid(0)),emptySet())}
        fails {repo.saveBattery(current,current,setOf(vid(2)),setOf(vid(23)))}
        assertEquals(before,repo.snapshot())
    }
    @Test fun archivesCannotBeEditedSoldAgainOrAllocatedAndNoSaleCommandsAreIntroduced()=runBlocking {
        val sold=dao.vehicles().single{it.id==vid(23)}
        val before=repo.snapshot()
        fails {repo.saveVehicle(sold,sold.id,fields(sold).copy(notes="Mutation"))}
        fails {repo.saveAssignment(sold.id,null,eid(0),1,4)}
        val live=dao.vehicles().single{it.id==vid(0)}
        fails {repo.saveVehicle(live,live.id,fields(live).copy(status=VehicleStatus.SOLD))}
        val body=before.bodies.single()
        fails {repo.saveBody(body,body.copy(currentVehicleId=sold.id))}
        assertEquals(before,repo.snapshot())
    }
    @Test fun fieldOnlyEditsEmitNewConsistentRoomSnapshots()=runBlocking {
        val ready=CompletableDeferred<Unit>();val updated=CompletableDeferred<CollectionData>()
        val job=launch(Dispatchers.Default) {repo.data.collect { d ->
            if(d.vehicles.any{it.id==vid(0) && it.name=="Émission réactive"}) updated.complete(d) else ready.complete(Unit)
        }}
        try {
            withTimeout(10_000){ready.await()}
            val v=dao.vehicles().single{it.id==vid(0)}
            repo.saveVehicle(v,v.id,fields(v).copy(name="Émission réactive"))
            val next=withTimeout(10_000){updated.await()}
            assertEquals(25,next.vehicles.size);assertEquals(6,next.equipment.size)
            assertEquals(dao.assignments(),next.assignments)
        } finally {job.cancelAndJoin()}
    }
    @Test fun savedCollectionSurvivesDatabaseCloseAndReopen()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="collection-persistence"
        context.deleteDatabase(name)
        fun fileDb()=Room.databaseBuilder(context,GarageDatabase::class.java,name)
            .addMigrations(GarageDatabase.MIGRATION_1_2,GarageDatabase.MIGRATION_2_3)
            .addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        val first=fileDb()
        GarageRepository(first).confirm(LegacyImporter.preview(bytes()))
        val r=CollectionRepository(first);val v=r.snapshot().vehicles.single{it.id==vid(0)}
        r.saveVehicle(v,v.id,fields(v).copy(name="Persistant",notes="Sauvegardé hors ligne"))
        val expected=r.snapshot();first.close()
        val second=fileDb()
        try {assertEquals(expected,CollectionRepository(second).snapshot());assertEquals(25,second.garage().vehicles().size)}
        finally {second.close();context.deleteDatabase(name)}
    }
    @Test fun radioReassignmentRollsBackMemoryCollisionsAndKeepsUnrelatedMemories()=runBlocking {
        val radio=Equipment(UUID.randomUUID(),EquipmentType.RADIO,reference="Autre radio",ownedQuantity=null)
        repo.saveEquipment(null,radio)
        repo.saveAssignment(vid(2),null,radio.id,1,1)
        val old=repo.snapshot().assignments.single{it.vehicleId==vid(0) && it.role==EquipmentType.RADIO}
        val before=repo.snapshot()
        fails {repo.saveAssignment(vid(0),old,radio.id,1,1)}
        assertEquals(before,repo.snapshot())
        repo.saveAssignment(vid(0),old,radio.id,1,2)
        val after=repo.snapshot()
        assertFalse(after.assignments.single{it.id==old.id}.isActive)
        assertFalse(after.memories.any{it.vehicleId==vid(0) && it.radioId==eid(0)})
        assertEquals(2,after.memories.single{it.vehicleId==vid(1) && it.radioId==eid(0)}.slot)
        assertEquals(2,after.memories.single{it.vehicleId==vid(0) && it.radioId==radio.id}.slot)
        assertNull(after.equipment.single{it.id==radio.id}.ownedQuantity)
        assertEquals(2,after.users(radio.id).size)
    }
    @Test fun receiverRemovalPreservesRadioSlotAndLinkedRadioCannotBeRemovedFirst()=runBlocking {
        val receiver=repo.snapshot().equipment.single{it.id==eid(1)}
        repo.saveEquipment(receiver,receiver.copy(linkedRadioId=eid(0)))
        val a=repo.snapshot().assignments.single{it.vehicleId==vid(0) && it.role==EquipmentType.RECEIVER}
        val radio=repo.snapshot().assignments.single{it.vehicleId==vid(0) && it.role==EquipmentType.RADIO}
        val memory=repo.snapshot().memories.single{it.vehicleId==vid(0)}
        val before=repo.snapshot()
        fails {repo.removeAssignment(radio)}
        assertEquals(before,repo.snapshot())
        repo.removeAssignment(a)
        val updated=repo.snapshot().memories.single{it.id==memory.id}
        assertEquals(memory.slot,updated.slot);assertNull(updated.receiverId)
        assertEquals(1L,repo.snapshot().available(repo.snapshot().equipment.single{it.id==eid(1)}))
        repo.removeAssignment(radio)
        assertFalse(repo.snapshot().memories.any{it.vehicleId==vid(0)})
        assertTrue(repo.snapshot().memories.any{it.vehicleId==vid(1)})
    }

}
