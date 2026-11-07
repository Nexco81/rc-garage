package fr.nexco.rcgarage.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Vehicle::class, Equipment::class, EquipmentUnit::class,
    EquipmentAssignment::class, RadioMemory::class, BodyShell::class, Battery::class,
    BatteryCompatibility::class, VehiclePhoto::class, Sale::class, SaleLine::class,
    StockMovement::class, SyncChange::class, ImportSource::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun garage(): GarageDao
    companion object {
        @Volatile private var instance: GarageDatabase? = null
        fun get(context: Context): GarageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GarageDatabase::class.java, "rc-garage.db")
                .addMigrations(MIGRATION_1_2).addCallback(INTEGRITY_CALLBACK).build().also { instance = it }
        }
        // v1: local model baseline. v2 adds immutable, byte-exact import receipts.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `import_sources` (`id` TEXT NOT NULL, `sha256` TEXT NOT NULL, `original` BLOB NOT NULL, `fileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `reportJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_import_sources_sha256` ON `import_sources` (`sha256`)")
                installIntegrity(db)
            }
        }
        val INTEGRITY_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = installIntegrity(db)
            override fun onOpen(db: SupportSQLiteDatabase) = installIntegrity(db)
        }
        fun installIntegrity(db: SupportSQLiteDatabase) {
            fun guard(table: String, suffix: String, operation: String, condition: String, message: String) {
                db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_$suffix BEFORE $operation ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, '$message'); END")
            }
            for (op in listOf("INSERT", "UPDATE")) {
                val key = op.lowercase()
                guard("vehicles", "status_$key", op, "NEW.status NOT IN ('BUILT','TO_BUILD','IN_PROGRESS','AWAITING_DELIVERY','COMPETITION_PREP','SOLD','TO_VERIFY','UNKNOWN')", "Invalid vehicle status")
                guard("equipment", "type_$key", op, "NEW.type NOT IN ('RADIO','RECEIVER','ESC','MOTOR','SERVO')", "Invalid equipment type")
                guard("assignments", "active_$key", op, "NEW.isActive NOT IN (0,1)", "Invalid active flag")
                guard("sales", "vehicle_link_$key", op, "EXISTS(SELECT 1 FROM vehicles WHERE saleId=NEW.id AND id!=NEW.vehicleId)", "Sale belongs to another vehicle")
                guard("vehicle_photos", "vehicle_link_$key", op, "EXISTS(SELECT 1 FROM vehicles WHERE primaryPhotoId=NEW.id AND (id!=NEW.vehicleId OR NEW.isPrimary=0))", "Invalid primary photo owner")
                guard("vehicles", "required_$key", op, "length(trim(NEW.brand))=0 OR length(trim(NEW.name))=0", "Brand and vehicle name are required")
                guard("vehicles", "sale_link_$key", op, "NEW.saleId IS NOT NULL AND EXISTS(SELECT 1 FROM sales WHERE id=NEW.saleId AND vehicleId!=NEW.id)", "Sale belongs to another vehicle")
                guard("vehicles", "photo_link_$key", op, "NEW.primaryPhotoId IS NOT NULL AND EXISTS(SELECT 1 FROM vehicle_photos WHERE id=NEW.primaryPhotoId AND (vehicleId!=NEW.id OR isPrimary=0))", "Invalid primary photo")
                guard("equipment", "receiver_link_$key", op, "NEW.linkedRadioId IS NOT NULL AND (NEW.type!='RECEIVER' OR EXISTS(SELECT 1 FROM equipment WHERE id=NEW.linkedRadioId AND type!='RADIO'))", "Invalid receiver radio link")
                guard("equipment", "radio_target_$key", op, "NEW.type!='RADIO' AND EXISTS(SELECT 1 FROM equipment WHERE linkedRadioId=NEW.id)", "Referenced radio must remain a radio")
                guard("equipment", "quantity_$key", op, "NEW.ownedQuantity<0 OR length(trim(NEW.reference))=0", "Invalid equipment quantity or reference")
                guard("equipment", "stock_$key", op, "EXISTS(SELECT 1 FROM assignments WHERE equipmentId=NEW.id AND isActive=1) AND (NEW.ownedQuantity IS NULL OR NEW.ownedQuantity<1 OR (NEW.type!='RADIO' AND NEW.ownedQuantity<(SELECT COALESCE(SUM(quantity),0) FROM assignments WHERE equipmentId=NEW.id AND isActive=1)))", "Insufficient owned stock")
                guard("equipment", "units_$key", op, "(SELECT COUNT(*) FROM equipment_units WHERE equipmentId=NEW.id)>COALESCE(NEW.ownedQuantity,0)", "Owned quantity below tracked units")
                guard("equipment_units", "stock_$key", op, "(SELECT COUNT(*) FROM equipment_units WHERE equipmentId=NEW.equipmentId AND id!=NEW.id)>=COALESCE((SELECT ownedQuantity FROM equipment WHERE id=NEW.equipmentId),0)", "Too many tracked units")
                guard("assignments", "valid_$key", op, "NEW.quantity<=0 OR (NEW.role='RADIO' AND NEW.quantity!=1) OR NEW.role!=(SELECT type FROM equipment WHERE id=NEW.equipmentId) OR (NEW.unitId IS NOT NULL AND (NEW.quantity!=1 OR NEW.equipmentId!=(SELECT equipmentId FROM equipment_units WHERE id=NEW.unitId)))", "Invalid assignment role quantity or unit")
                guard("assignments", "receiver_radio_$key", op, "NEW.isActive=1 AND NEW.role='RECEIVER' AND EXISTS(SELECT 1 FROM equipment e WHERE e.id=NEW.equipmentId AND e.linkedRadioId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM assignments a WHERE a.vehicleId=NEW.vehicleId AND a.equipmentId=e.linkedRadioId AND a.isActive=1))", "Receiver requires its linked radio")
                guard("assignments", "stock_$key", op, "NEW.isActive=1 AND (COALESCE((SELECT ownedQuantity FROM equipment WHERE id=NEW.equipmentId),0)<1 OR ((SELECT type FROM equipment WHERE id=NEW.equipmentId)!='RADIO' AND NEW.quantity+(SELECT COALESCE(SUM(quantity),0) FROM assignments WHERE equipmentId=NEW.equipmentId AND isActive=1 AND id!=NEW.id)>(SELECT ownedQuantity FROM equipment WHERE id=NEW.equipmentId)))", "Insufficient available stock")
                guard("assignments", "unit_$key", op, "NEW.isActive=1 AND NEW.unitId IS NOT NULL AND NEW.role!='RADIO' AND EXISTS(SELECT 1 FROM assignments WHERE unitId=NEW.unitId AND isActive=1 AND id!=NEW.id)", "Unit already assigned")
                guard("radio_memories", "types_$key", op, "NEW.slot<1 OR (SELECT type FROM equipment WHERE id=NEW.radioId)!='RADIO' OR (NEW.receiverId IS NOT NULL AND (SELECT type FROM equipment WHERE id=NEW.receiverId)!='RECEIVER')", "Invalid radio memory")
                guard("batteries", "values_$key", op, "NEW.ownedQuantity<0 OR NEW.capacityMah<=0 OR NEW.cells<=0 OR NEW.voltage<=0 OR NEW.cRating<0", "Invalid battery specification")
                guard("vehicle_photos", "order_$key", op, "NEW.orderIndex<0 OR (NEW.isPrimary=1 AND EXISTS(SELECT 1 FROM vehicle_photos WHERE vehicleId=NEW.vehicleId AND isPrimary=1 AND id!=NEW.id))", "Invalid photo order or primary photo")
                guard("body_shells", "origin_$key", op, "NEW.isOriginal=1 AND NEW.originVehicleId IS NULL", "Original body requires provenance")
                guard("sale_lines", "quantity_$key", op, "NEW.quantity<=0", "Sale quantity must be positive")
                guard("sales", "history_$key", op, "NEW.historyKnown=0 AND NEW.saleType!='UNKNOWN'", "Unknown historical sale must stay unknown")
                guard("sync_changes", "revision_$key", op, "NEW.revision<1", "Revision must be positive")
            }
            guard("body_shells", "provenance", "UPDATE", "OLD.isOriginal=1 AND (NEW.isOriginal IS NOT 1 OR NEW.originVehicleId IS NOT OLD.originVehicleId)", "Original body provenance is immutable")
            guard("equipment", "type_history", "UPDATE", "NEW.type!=OLD.type AND EXISTS(SELECT 1 FROM assignments WHERE equipmentId=OLD.id)", "Equipment with history cannot change type")
            guard("equipment_units", "identity", "UPDATE", "NEW.id!=OLD.id OR NEW.equipmentId!=OLD.equipmentId", "Unit provenance is immutable")
            guard("equipment", "receiver_allocations", "UPDATE", "NEW.linkedRadioId IS NOT NULL AND EXISTS(SELECT 1 FROM assignments a WHERE a.equipmentId=NEW.id AND a.isActive=1 AND NOT EXISTS(SELECT 1 FROM assignments r WHERE r.vehicleId=a.vehicleId AND r.equipmentId=NEW.linkedRadioId AND r.isActive=1))", "Receiver assignments require linked radio")
            guard("assignments", "radio_release", "UPDATE", "OLD.role='RADIO' AND NEW.isActive=0 AND NOT EXISTS(SELECT 1 FROM assignments r WHERE r.vehicleId=OLD.vehicleId AND r.equipmentId=OLD.equipmentId AND r.isActive=1 AND r.id!=OLD.id) AND EXISTS(SELECT 1 FROM assignments a JOIN equipment e ON e.id=a.equipmentId WHERE a.vehicleId=OLD.vehicleId AND a.isActive=1 AND e.linkedRadioId=OLD.equipmentId)", "Release linked receivers before radio")
            guard("assignments", "history", "UPDATE", "OLD.isActive=0 OR NEW.id!=OLD.id OR NEW.vehicleId!=OLD.vehicleId OR NEW.equipmentId!=OLD.equipmentId OR NEW.role!=OLD.role OR NEW.quantity!=OLD.quantity OR NEW.unitId IS NOT OLD.unitId OR NEW.createdAt IS NOT OLD.createdAt", "Release then create an assignment preserving history")
            guard("assignments", "no_delete", "DELETE", "1", "Assignment history is immutable")
            for (table in listOf("sales", "sale_lines", "stock_movements", "import_sources")) {
                guard(table, "immutable_update", "UPDATE", "1", "Journal is immutable")
                guard(table, "immutable_delete", "DELETE", "1", "Journal is immutable")
            }
        }
    }
}
