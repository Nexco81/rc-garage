package fr.nexco.rcgarage.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface GarageDao {
    @Query("SELECT * FROM vehicles ORDER BY brand, name") fun observeVehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM equipment ORDER BY reference") fun observeEquipment(): Flow<List<Equipment>>
    @Query("SELECT * FROM body_shells ORDER BY name") fun observeBodies(): Flow<List<BodyShell>>
    @Query("SELECT DISTINCT v.* FROM vehicles v JOIN assignments a ON a.vehicleId=v.id WHERE a.equipmentId=:id AND a.isActive=1 ORDER BY v.name")
    fun observeAssignedVehicles(id: UUID): Flow<List<Vehicle>>
    @Query("SELECT * FROM assignments WHERE equipmentId=:id ORDER BY createdAt") fun observeAssignmentHistory(id: UUID): Flow<List<EquipmentAssignment>>
    @Query("SELECT * FROM equipment_units WHERE equipmentId=:id") fun observeUnits(id: UUID): Flow<List<EquipmentUnit>>
    @Query("SELECT * FROM vehicle_photos WHERE vehicleId=:id ORDER BY orderIndex") fun observePhotos(id: UUID): Flow<List<VehiclePhoto>>
    @Query("SELECT * FROM stock_movements WHERE equipmentId=:id ORDER BY timestamp") fun observeStockMovements(id: UUID): Flow<List<StockMovement>>
    @Query("SELECT * FROM sale_lines WHERE saleId=:id") fun observeSaleLines(id: UUID): Flow<List<SaleLine>>
    @Query("SELECT * FROM battery_compatibility WHERE vehicleId=:id") fun observeBatteryCompatibility(id: UUID): Flow<List<BatteryCompatibility>>
    @Query("SELECT * FROM sync_changes WHERE status='PENDING' ORDER BY timestamp") fun observePendingChanges(): Flow<List<SyncChange>>
    @Query("SELECT * FROM batteries") fun observeBatteries(): Flow<List<Battery>>
    @Query("SELECT * FROM sales ORDER BY createdAt DESC") fun observeSales(): Flow<List<Sale>>
    @Query("SELECT * FROM vehicles") suspend fun vehicles(): List<Vehicle>
    @Query("SELECT * FROM equipment") suspend fun equipment(): List<Equipment>
    @Query("SELECT * FROM assignments") suspend fun assignments(): List<EquipmentAssignment>
    @Query("SELECT * FROM body_shells") suspend fun bodies(): List<BodyShell>
    @Query("SELECT * FROM radio_memories") suspend fun memories(): List<RadioMemory>
    @Query("SELECT * FROM sales") suspend fun sales(): List<Sale>
    @Query("SELECT EXISTS(SELECT 1 FROM import_sources WHERE sha256=:hash)") suspend fun sourceExists(hash: String): Boolean
    @Query("SELECT * FROM import_sources WHERE sha256=:hash") suspend fun source(hash: String): ImportSource?
    @Query("SELECT COUNT(*) FROM import_sources") suspend fun sourceCount(): Int
    @Query("SELECT CASE WHEN type='RADIO' THEN ownedQuantity ELSE ownedQuantity-(SELECT COALESCE(SUM(quantity),0) FROM assignments WHERE equipmentId=equipment.id AND isActive=1) END FROM equipment WHERE id=:id")
    suspend fun availableQuantity(id: UUID): Int?
    @Insert suspend fun insertVehicle(value: Vehicle)
    @Insert suspend fun insertEquipment(value: Equipment)
    @Insert suspend fun insertUnit(value: EquipmentUnit)
    @Insert suspend fun insertAssignment(value: EquipmentAssignment)
    @Insert suspend fun insertMemory(value: RadioMemory)
    @Insert suspend fun insertBody(value: BodyShell)
    @Query("UPDATE body_shells SET currentVehicleId=:vehicleId WHERE id=:id") suspend fun moveBody(id: UUID, vehicleId: UUID?)
    @Insert suspend fun insertBattery(value: Battery)
    @Insert suspend fun insertCompatibility(value: BatteryCompatibility)
    @Insert suspend fun insertPhoto(value: VehiclePhoto)
    @Insert suspend fun insertSale(value: Sale)
    @Insert suspend fun insertSaleLine(value: SaleLine)
    @Insert suspend fun insertMovement(value: StockMovement)
    @Insert suspend fun insertSyncChange(value: SyncChange)
    @Insert suspend fun insertSource(value: ImportSource)
    @Query("UPDATE equipment SET ownedQuantity=:quantity WHERE id=:id") suspend fun setOwnedQuantity(id: UUID, quantity: Int?)
    @Query("UPDATE assignments SET isActive=0 WHERE id=:id AND isActive=1") suspend fun releaseAssignment(id: UUID): Int
}
