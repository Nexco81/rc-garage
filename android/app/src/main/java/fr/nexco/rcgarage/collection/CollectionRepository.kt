package fr.nexco.rcgarage.collection

import androidx.room.withTransaction
import fr.nexco.rcgarage.data.*
import kotlinx.coroutines.flow.map
import java.util.UUID

data class CollectionData(
    val vehicles: List<Vehicle> = emptyList(), val equipment: List<Equipment> = emptyList(),
    val bodies: List<BodyShell> = emptyList(), val batteries: List<Battery> = emptyList(),
    val assignments: List<EquipmentAssignment> = emptyList(), val memories: List<RadioMemory> = emptyList(),
    val compatibility: List<BatteryCompatibility> = emptyList(), val sales: List<Sale> = emptyList(),
) {
    fun chassisSuggestions(brand: String) = vehicles.filter{it.brand.trim().equals(brand.trim(),true)}
        .mapNotNull{it.chassis?.takeIf(String::isNotBlank)}.distinct().sorted()
    fun vehicleBrands() = vehicles.map{it.brand}.filter(String::isNotBlank).distinct().sorted()
    fun equipmentBrands() = equipment.mapNotNull{it.brand}.filter(String::isNotBlank).distinct().sorted()
    fun bodyBrands() = bodies.mapNotNull{it.brand}.filter(String::isNotBlank).distinct().sorted()
    fun batteryBrands() = batteries.mapNotNull{it.brand}.filter(String::isNotBlank).distinct().sorted()
    fun assigned(id: UUID) = assignments.filter{it.equipmentId==id && it.isActive}
    fun users(id: UUID) = vehicles.filter { v -> assigned(id).any{it.vehicleId==v.id} }
    fun available(e: Equipment): Long? = e.ownedQuantity?.toLong()?.let { owned ->
        if(e.type==EquipmentType.RADIO) owned else owned-assigned(e.id).sumOf{it.quantity.toLong()}
    }
    fun filteredVehicles(search: String,status: VehicleStatus?) = vehicles.filter { v ->
        (status==null || v.status==status) && listOfNotNull(v.brand,v.name,v.chassis,v.notes,v.usage)
            .any{it.contains(search.trim(),true)}
    }.sortedWith(compareBy({it.brand.lowercase()},{it.name.lowercase()}))
}

val Vehicle.archived: Boolean get() = status==VehicleStatus.SOLD || saleId!=null
val Vehicle.originElectronicsVisible: Boolean get() = isRtr==true && electronicsCustomized!=true

data class VehicleFields(val brand: String,val name: String,val chassis: String?,val status: VehicleStatus,
    val usage: String?,val notes: String?,val isRtr: Boolean?,val electronicsCustomized: Boolean?)

class CollectionRepository(private val db: GarageDatabase) {
    private val dao=db.collection()
    private val garage=db.garage()
    val data=dao.changes().map { snapshot() }
    suspend fun snapshot(): CollectionData = db.withTransaction {
        CollectionData(garage.vehicles(),garage.equipment(),garage.bodies(),dao.batteries(),
            garage.assignments(),garage.memories(),dao.compatibility(),garage.sales())
    }
    private fun <T> unchanged(expected: T?,current: T?) {
        require(expected==current) { "Cette fiche a changé. Fermez l’éditeur puis rechargez-la avant de réessayer." }
    }
    private suspend fun editableVehicle(id: UUID): Vehicle {
        val v=requireNotNull(dao.vehicle(id)){"Modèle introuvable"}
        require(!v.archived){"Archive vendue en lecture seule. Vente et restauration ne font pas partie de cette étape."}
        return v
    }
    suspend fun saveVehicle(expected: Vehicle?,id: UUID,fields: VehicleFields) = db.withTransaction {
        unchanged(expected,dao.vehicle(id))
        require(expected==null || !expected.archived){"Archive vendue en lecture seule"}
        require(fields.brand.isNotBlank() && fields.name.isNotBlank()) { "Marque et nom du modèle obligatoires" }
        require(fields.status!=VehicleStatus.SOLD){"La vente sera traitée dans une étape dédiée"}
        val seed=expected ?: Vehicle(id,fields.brand,fields.name)
        require(seed.id==id)
        val value=seed.copy(brand=fields.brand.trim(),name=fields.name.trim(),chassis=fields.chassis,
            status=fields.status,legacyStatus=if(seed.status==fields.status) seed.legacyStatus else fields.status.label(),
            usage=fields.usage,notes=fields.notes,isRtr=fields.isRtr,electronicsCustomized=fields.electronicsCustomized,
            updatedAt=System.currentTimeMillis())
        if(expected==null) garage.insertVehicle(value) else check(dao.updateVehicle(value)==1)
        // Changing Kit/RTR visibility must never release or erase assignments or original electronics.
    }
    suspend fun saveEquipment(expected: Equipment?,value: Equipment) = db.withTransaction {
        unchanged(expected,dao.equipment(value.id))
        require(value.reference.isNotBlank()) { "Référence obligatoire" }
        require(value.ownedQuantity==null || value.ownedQuantity>=0) { "Quantité possédée invalide" }
        require(expected==null || value.specsJson==expected.specsJson) { "Les spécifications importées doivent être conservées" }
        val saved=value.copy(reference=value.reference.trim(),updatedAt=System.currentTimeMillis())
        if(expected==null) garage.insertEquipment(saved) else check(dao.updateEquipment(saved)==1)
    }
    suspend fun saveBody(expected: BodyShell?,value: BodyShell) = db.withTransaction {
        unchanged(expected,dao.body(value.id))
        require(!value.name.isNullOrBlank() || !value.reference.isNullOrBlank()){ "Nom ou référence de carrosserie obligatoire" }
        expected?.currentVehicleId?.let { editableVehicle(it) }
        value.currentVehicleId?.let { editableVehicle(it) }
        if(expected!=null) require(value.originVehicleId==expected.originVehicleId && value.isOriginal==expected.isOriginal) {
            "La provenance de la carrosserie doit être conservée"
        }
        if(expected==null) garage.insertBody(value) else check(dao.updateBody(value)==1)
    }
    suspend fun saveBattery(expected: Battery?,value: Battery,expectedLinks: Set<UUID>,links: Set<UUID>) = db.withTransaction {
        unchanged(expected,dao.battery(value.id))
        val current=dao.compatibility().filter{it.batteryId==value.id}.map{it.vehicleId}.toSet()
        unchanged(expectedLinks,current)
        require(!value.reference.isNullOrBlank()){ "Référence de batterie obligatoire" }
        require(value.ownedQuantity==null || value.ownedQuantity>=0){"Quantité possédée invalide"}
        require(value.capacityMah==null || value.capacityMah>0){"Capacité strictement positive"}
        require(value.cells==null || value.cells>0){"Nombre de cellules strictement positif"}
        require(value.voltage==null || (value.voltage.isFinite() && value.voltage>0)){"Tension invalide"}
        require(value.cRating==null || (value.cRating.isFinite() && value.cRating>=0)){"Taux C invalide"}
        ((current-links)+(links-current)).forEach { editableVehicle(it) }
        if(expected==null) garage.insertBattery(value) else check(dao.updateBattery(value)==1)
        dao.clearCompatibility(value.id)
        links.forEach { garage.insertCompatibility(BatteryCompatibility(value.id,it)) }
    }
    suspend fun saveAssignment(vehicleId: UUID,expected: EquipmentAssignment?,equipmentId: UUID,
        quantity: Int,memorySlot: Int?,expectedType: EquipmentType?=expected?.role) = db.withTransaction {
        val vehicle=editableVehicle(vehicleId)
        require(!vehicle.originElectronicsVisible){"Activez et enregistrez la personnalisation RTR pour modifier les affectations"}
        val equipment=requireNotNull(dao.equipment(equipmentId)){"Équipement introuvable"}
        require(expectedType==null || equipment.type==expectedType){"La catégorie de cette référence a changé. Rechargez la fiche."}
        val all=garage.assignments()
        if(expected!=null) {
            unchanged(expected,all.find{it.id==expected.id})
            require(expected.isActive && expected.vehicleId==vehicleId && expected.role==equipment.type){"Affectation incompatible"}
            require(expected.unitId==null || expected.equipmentId!=equipmentId || quantity==1) {
                "Cette affectation identifie une unité physique : sa quantité doit rester 1."
            }
        }
        require(quantity>0){"Quantité affectée strictement positive"}
        if(equipment.type==EquipmentType.RADIO) {
            require(quantity==1){"Une association radio ne consomme pas de stock"}
            require(all.none{it.isActive && it.vehicleId==vehicleId && it.equipmentId==equipmentId && it.id!=expected?.id}){
                "Cette radio est déjà associée à ce modèle"
            }
        } else require(memorySlot==null){"Mémoire réservée aux radios"}
        require(memorySlot==null || memorySlot>0){"Mémoire radio strictement positive"}
        val changed=expected==null || expected.equipmentId!=equipmentId || expected.quantity!=quantity
        if(changed) {
            if(expected!=null) garage.releaseAssignment(expected.id)
            garage.insertAssignment(EquipmentAssignment(UUID.randomUUID(),vehicleId,equipmentId,equipment.type,quantity,
                unitId=expected?.unitId?.takeIf{expected.equipmentId==equipmentId && quantity==1},createdAt=System.currentTimeMillis()))
        }
        cleanMemories(vehicleId)
        if(equipment.type==EquipmentType.RADIO) {
            val old=garage.memories().find{it.vehicleId==vehicleId && it.radioId==equipmentId}
            if(old!=null) dao.deleteMemory(old.id)
            if(memorySlot!=null) garage.insertMemory(RadioMemory(old?.id ?: UUID.randomUUID(),equipmentId,memorySlot,vehicleId,
                old?.receiverId))
        }
    }
    suspend fun removeAssignment(expected: EquipmentAssignment) = db.withTransaction {
        val v=editableVehicle(expected.vehicleId)
        require(!v.originElectronicsVisible){"Les affectations RTR masquées sont conservées"}
        unchanged(expected,garage.assignments().find{it.id==expected.id})
        require(expected.isActive){"Affectation déjà retirée"}
        garage.releaseAssignment(expected.id)
        cleanMemories(expected.vehicleId)
    }
    private suspend fun cleanMemories(vehicleId: UUID) {
        val active=garage.assignments().filter{it.isActive && it.vehicleId==vehicleId}
        for(m in garage.memories().filter{it.vehicleId==vehicleId}) {
            if(active.none{it.equipmentId==m.radioId}) dao.deleteMemory(m.id)
            else if(m.receiverId!=null && active.none{it.equipmentId==m.receiverId}) dao.updateMemory(m.copy(receiverId=null))
        }
    }
}

fun VehicleStatus.label(): String = when(this) {
    VehicleStatus.BUILT -> "Monté"
    VehicleStatus.TO_BUILD -> "À monter"
    VehicleStatus.IN_PROGRESS -> "En cours"
    VehicleStatus.AWAITING_DELIVERY -> "En attente de réception"
    VehicleStatus.COMPETITION_PREP -> "Préparation compétition"
    VehicleStatus.SOLD -> "Vendu"
    VehicleStatus.TO_VERIFY -> "À vérifier"
    VehicleStatus.UNKNOWN -> "Inconnu"
}
fun EquipmentType.label(): String = when(this) {
    EquipmentType.RADIO -> "Radio"
    EquipmentType.RECEIVER -> "Récepteur"
    EquipmentType.ESC -> "ESC"
    EquipmentType.MOTOR -> "Moteur"
    EquipmentType.SERVO -> "Servo de direction"
}
