package fr.nexco.rcgarage.data

import androidx.room.withTransaction
import fr.nexco.rcgarage.importer.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.UUID

sealed interface ImportOutcome {
    data class Success(val vehicles: Int, val equipment: Int, val bodies: Int,
        val duplicateRows: Int, val alreadyImported: Boolean = false) : ImportOutcome
    data class Failure(val errors: List<String>) : ImportOutcome
}

class GarageRepository(private val database: GarageDatabase) {
    private val dao = database.garage()
    val vehicles = dao.observeVehicles()
    val equipment = dao.observeEquipment()
    val bodies = dao.observeBodies()
    val batteries = dao.observeBatteries()
    val sales = dao.observeSales()
    fun assignedVehicles(id: UUID) = dao.observeAssignedVehicles(id)
    fun assignmentHistory(id: UUID) = dao.observeAssignmentHistory(id)
    suspend fun availableQuantity(id: UUID) = dao.availableQuantity(id)

    /** These operations leave an append-only assignment history for future sale/restore commands. */
    suspend fun assign(value: EquipmentAssignment) = database.withTransaction { dao.insertAssignment(value) }
    suspend fun moveBody(id: UUID, vehicleId: UUID?) = database.withTransaction { dao.moveBody(id,vehicleId) }
    suspend fun release(id: UUID) = database.withTransaction { dao.releaseAssignment(id) }
    suspend fun setOwnedQuantity(id: UUID, quantity: Int?) = database.withTransaction { dao.setOwnedQuantity(id,quantity) }

    suspend fun confirm(preview: ImportPreview): ImportOutcome {
        // Never trust a UI count or a stale validation result: parse the preserved bytes again.
        val checked = LegacyImporter.preview(preview.originalBytes(),preview.fileName)
        if (!checked.canConfirm) return ImportOutcome.Failure(checked.errors)
        if (checked.sha256 != preview.sha256) return ImportOutcome.Failure(listOf("Le fichier a changé depuis l’aperçu"))
        return try {
            database.withTransaction {
                if (dao.sourceExists(checked.sha256)) return@withTransaction ImportOutcome.Success(0,0,0,0,true)
                val plan=checked.plan
                var duplicates=0
                suspend fun <T> insert(rows: List<T>, existing: List<T>, id: (T)->UUID, write: suspend (T)->Unit): Int {
                    val byId=existing.associateBy(id)
                    var added=0
                    for(row in rows) {
                        val old=byId[id(row)]
                        if(old==null) { write(row); added++ }
                        else { require(old==row || (old is Equipment && row is Equipment && LegacyImporter.sameEquipment(old,row))) { "Conflit avec une donnée existante : ${id(row)}. Aucun changement appliqué." }; duplicates++ }
                    }
                    return added
                }
                val nv=insert(plan.vehicles,dao.vehicles(),Vehicle::id,dao::insertVehicle)
                val ne=insert(plan.equipment,dao.equipment(),Equipment::id,dao::insertEquipment)
                insert(plan.sales,dao.sales(),Sale::id,dao::insertSale)
                val nb=insert(plan.bodies,dao.bodies(),BodyShell::id,dao::insertBody)
                insert(plan.assignments,dao.assignments(),EquipmentAssignment::id,dao::insertAssignment)
                insert(plan.memories,dao.memories(),RadioMemory::id,dao::insertMemory)
                val report=JSONObject().put("vehicles",nv).put("equipment",ne).put("bodies",nb)
                    .put("duplicates",duplicates).put("warnings",org.json.JSONArray(checked.warnings)).toString()
                dao.insertSource(ImportSource(LegacyImporter.stableId("import",checked.sha256),checked.sha256,
                    checked.originalBytes(),checked.fileName,System.currentTimeMillis(),report))
                ImportOutcome.Success(nv,ne,nb,duplicates)
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { ImportOutcome.Failure(listOf(e.message ?: "Import annulé, transaction restaurée")) }
    }
}
