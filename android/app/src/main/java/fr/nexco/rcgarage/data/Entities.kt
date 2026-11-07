package fr.nexco.rcgarage.data

import androidx.room.*
import java.util.UUID

enum class VehicleStatus { BUILT, TO_BUILD, IN_PROGRESS, AWAITING_DELIVERY, COMPETITION_PREP, SOLD, TO_VERIFY, UNKNOWN }
enum class EquipmentType { RADIO, RECEIVER, ESC, MOTOR, SERVO }
enum class SaleType { UNKNOWN, COMPLETE, PARTIAL }
enum class Disposition { UNKNOWN, SOLD, KEPT }
enum class SyncStatus { PENDING, CONFLICT, SYNCED }

@Entity(tableName = "vehicles", foreignKeys = [
    ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"], deferred = true),
    ForeignKey(entity = VehiclePhoto::class, parentColumns = ["id"], childColumns = ["primaryPhotoId"], deferred = true),
], indices = [Index("saleId"), Index("primaryPhotoId")])
data class Vehicle(
    @PrimaryKey val id: UUID, val brand: String, val name: String,
    val chassis: String? = null, val usage: String? = null, val category: String? = null, val scale: String? = null,
    val status: VehicleStatus = VehicleStatus.UNKNOWN, val legacyStatus: String? = null,
    val isRtr: Boolean? = null, val electronicsCustomized: Boolean? = null,
    val originalElectronicsJson: String? = null, val notes: String? = null,
    val primaryPhotoId: UUID? = null, val saleId: UUID? = null, val updatedAt: Long? = null,
)

@Entity(tableName = "equipment", foreignKeys = [ForeignKey(entity = Equipment::class,
    parentColumns = ["id"], childColumns = ["linkedRadioId"], deferred = true)], indices = [Index("linkedRadioId")])
data class Equipment(@PrimaryKey val id: UUID, val type: EquipmentType, val brand: String? = null,
    val reference: String, val ownedQuantity: Int? = null, val inventoryLabel: String? = null,
    val specsJson: String? = null, val notes: String? = null, val updatedAt: Long? = null, val linkedRadioId: UUID? = null)

@Entity(tableName = "equipment_units", foreignKeys = [ForeignKey(entity = Equipment::class,
    parentColumns = ["id"], childColumns = ["equipmentId"])], indices = [Index("equipmentId")])
data class EquipmentUnit(@PrimaryKey val id: UUID, val equipmentId: UUID, val serial: String? = null,
    val inventoryLabel: String? = null)

@Entity(tableName = "assignments", foreignKeys = [
    ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"]),
    ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["equipmentId"]),
    ForeignKey(entity = EquipmentUnit::class, parentColumns = ["id"], childColumns = ["unitId"]),
], indices = [Index("vehicleId"), Index("equipmentId"), Index("unitId")])
data class EquipmentAssignment(@PrimaryKey val id: UUID, val vehicleId: UUID, val equipmentId: UUID,
    val role: EquipmentType, val quantity: Int = 1, val isActive: Boolean = true,
    val unitId: UUID? = null, val createdAt: Long? = null)

@Entity(tableName = "radio_memories", foreignKeys = [
    ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["radioId"]),
    ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["receiverId"]),
    ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"]),
], indices = [Index(value = ["radioId", "slot"], unique = true), Index("receiverId"), Index("vehicleId")])
data class RadioMemory(@PrimaryKey val id: UUID, val radioId: UUID, val slot: Int,
    val vehicleId: UUID, val receiverId: UUID? = null)

@Entity(tableName = "body_shells", foreignKeys = [
    ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["originVehicleId"]),
    ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["currentVehicleId"]),
], indices = [Index("originVehicleId"), Index("currentVehicleId")])
data class BodyShell(@PrimaryKey val id: UUID, val originVehicleId: UUID? = null,
    val currentVehicleId: UUID? = null, val isOriginal: Boolean? = null, val brand: String? = null,
    val name: String? = null, val reference: String? = null, val condition: String? = null,
    val decoration: String? = null, val notes: String? = null, val photo: String? = null,
    val compatible: String? = null)

@Entity(tableName = "batteries")
data class Battery(@PrimaryKey val id: UUID, val brand: String? = null, val reference: String? = null,
    val chemistry: String? = null, val capacityMah: Int? = null, val cells: Int? = null,
    val voltage: Double? = null, val cRating: Double? = null, val connector: String? = null,
    val ownedQuantity: Int? = null)

@Entity(tableName = "battery_compatibility", primaryKeys = ["batteryId", "vehicleId"], foreignKeys = [
    ForeignKey(entity = Battery::class, parentColumns = ["id"], childColumns = ["batteryId"]),
    ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"]),
], indices = [Index("vehicleId")])
data class BatteryCompatibility(val batteryId: UUID, val vehicleId: UUID)

@Entity(tableName = "vehicle_photos", foreignKeys = [ForeignKey(entity = Vehicle::class,
    parentColumns = ["id"], childColumns = ["vehicleId"])], indices = [Index("vehicleId")])
data class VehiclePhoto(@PrimaryKey val id: UUID, val vehicleId: UUID, val uri: String,
    val orderIndex: Int, val isPrimary: Boolean)

@Entity(tableName = "sales", foreignKeys = [ForeignKey(entity = Vehicle::class,
    parentColumns = ["id"], childColumns = ["vehicleId"], deferred = true)], indices = [Index("vehicleId")])
data class Sale(@PrimaryKey val id: UUID, val vehicleId: UUID, val createdAt: Long? = null,
    val saleType: SaleType = SaleType.UNKNOWN, val historyKnown: Boolean = false, val snapshotJson: String)

@Entity(tableName = "sale_lines", foreignKeys = [
    ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"]),
    ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["equipmentId"]),
], indices = [Index("saleId"), Index("equipmentId")])
data class SaleLine(@PrimaryKey val id: UUID, val saleId: UUID, val equipmentId: UUID? = null,
    val equipmentSnapshotJson: String, val role: EquipmentType, val disposition: Disposition,
    val quantity: Int)

@Entity(tableName = "stock_movements", foreignKeys = [
    ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["equipmentId"]),
    ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"]),
], indices = [Index("equipmentId"), Index("saleId")])
data class StockMovement(@PrimaryKey val id: UUID, val equipmentId: UUID, val deltaOwned: Int,
    val reason: String, val saleId: UUID? = null, val timestamp: Long)

@Entity(tableName = "sync_changes", indices = [Index(value = ["entityType", "entityId", "revision"], unique = true)])
data class SyncChange(@PrimaryKey val id: UUID, val entityType: String, val entityId: UUID,
    val revision: Long, val status: SyncStatus, val timestamp: Long)

/** Exact immutable original bytes, never a rewritten JSON export. */
@Entity(tableName = "import_sources", indices = [Index(value = ["sha256"], unique = true)])
data class ImportSource(@PrimaryKey val id: UUID, val sha256: String, val original: ByteArray,
    val fileName: String, val importedAt: Long, val reportJson: String)
