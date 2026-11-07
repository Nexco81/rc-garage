package fr.nexco.rcgarage.data

import androidx.room.TypeConverter
import java.util.UUID

class Converters {
    @TypeConverter fun uuid(value: String?): UUID? = value?.let(UUID::fromString)
    @TypeConverter fun uuid(value: UUID?): String? = value?.toString()
    @TypeConverter fun status(value: String): VehicleStatus = VehicleStatus.valueOf(value)
    @TypeConverter fun status(value: VehicleStatus): String = value.name
    @TypeConverter fun equipment(value: String): EquipmentType = EquipmentType.valueOf(value)
    @TypeConverter fun equipment(value: EquipmentType): String = value.name
    @TypeConverter fun sale(value: String): SaleType = SaleType.valueOf(value)
    @TypeConverter fun sale(value: SaleType): String = value.name
    @TypeConverter fun disposition(value: String): Disposition = Disposition.valueOf(value)
    @TypeConverter fun disposition(value: Disposition): String = value.name
    @TypeConverter fun sync(value: String): SyncStatus = SyncStatus.valueOf(value)
    @TypeConverter fun sync(value: SyncStatus): String = value.name
}
