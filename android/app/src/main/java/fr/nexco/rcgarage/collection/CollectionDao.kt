package fr.nexco.rcgarage.collection

import androidx.room.*
import fr.nexco.rcgarage.data.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface CollectionDao {
    // Each invalidation triggers a consistent transactional snapshot, including field-only edits.
    @Query("SELECT (SELECT COUNT(*) FROM vehicles)+(SELECT COUNT(*) FROM equipment)+(SELECT COUNT(*) FROM assignments)+(SELECT COUNT(*) FROM body_shells)+(SELECT COUNT(*) FROM batteries)+(SELECT COUNT(*) FROM battery_compatibility)+(SELECT COUNT(*) FROM radio_memories)+(SELECT COUNT(*) FROM sales)")
    fun changes(): Flow<Long>
    @Query("SELECT * FROM vehicles WHERE id=:id") suspend fun vehicle(id: UUID): Vehicle?
    @Query("SELECT * FROM equipment WHERE id=:id") suspend fun equipment(id: UUID): Equipment?
    @Query("SELECT * FROM body_shells WHERE id=:id") suspend fun body(id: UUID): BodyShell?
    @Query("SELECT * FROM batteries WHERE id=:id") suspend fun battery(id: UUID): Battery?
    @Query("SELECT * FROM batteries ORDER BY brand,reference") suspend fun batteries(): List<Battery>
    @Query("SELECT * FROM battery_compatibility") suspend fun compatibility(): List<BatteryCompatibility>
    @Update suspend fun updateVehicle(value: Vehicle): Int
    @Update suspend fun updateEquipment(value: Equipment): Int
    @Update suspend fun updateBody(value: BodyShell): Int
    @Update suspend fun updateBattery(value: Battery): Int
    @Query("DELETE FROM radio_memories WHERE id=:id") suspend fun deleteMemory(id: UUID)
    @Update suspend fun updateMemory(value: RadioMemory)
    @Query("DELETE FROM battery_compatibility WHERE batteryId=:id") suspend fun clearCompatibility(id: UUID)
}
