package fr.nexco.rcgarage.collection

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nexco.rcgarage.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

sealed interface EditorSession {
    data class VehicleEdit(val initial: Vehicle,val new: Boolean=false): EditorSession
    data class EquipmentEdit(val initial: Equipment,val new: Boolean=false): EditorSession
    data class BodyEdit(val initial: BodyShell,val new: Boolean=false): EditorSession
    data class BatteryEdit(val initial: Battery,val links: Set<UUID>,val new: Boolean=false): EditorSession
    data class AssignmentEdit(val vehicleId: UUID,val initial: EquipmentAssignment?=null,val memory: Int?=null): EditorSession
    data class RemoveAssignment(val initial: EquipmentAssignment): EditorSession
}
data class CollectionUiState(val loading: Boolean=true,val data: CollectionData=CollectionData(),val error: String?=null)

class CollectionViewModel(application: Application): AndroidViewModel(application) {
    private val repository=CollectionRepository(GarageDatabase.get(application))
    private val refresh=MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state=refresh.flatMapLatest {
        repository.data.map{CollectionUiState(loading=false,data=it)}
            .onStart { emit(CollectionUiState()) }.catch { emit(CollectionUiState(loading=false,error=it.message ?: "Lecture impossible")) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),CollectionUiState())
    var editor by mutableStateOf<EditorSession?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    fun retry() { refresh.value++ }
    fun edit(session: EditorSession) { if(!busy) { editor=session;error=null;message=null } }
    fun dismiss() { if(!busy) { editor=null;error=null } }
    private fun perform(block: suspend ()->Unit) {
        if(busy) return
        busy=true;error=null;message=null
        viewModelScope.launch {
            try { block();editor=null;message="Enregistré" }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=readableError(e) }
            finally { busy=false }
        }
    }
    fun saveVehicle(session: EditorSession.VehicleEdit,fields: VehicleFields) = perform {
        repository.saveVehicle(session.initial.takeUnless{session.new},session.initial.id,fields)
    }
    fun saveEquipment(session: EditorSession.EquipmentEdit,value: ()->Equipment) = perform {
        repository.saveEquipment(session.initial.takeUnless{session.new},value())
    }
    fun saveBody(session: EditorSession.BodyEdit,value: BodyShell) = perform {
        repository.saveBody(session.initial.takeUnless{session.new},value)
    }
    fun saveBattery(session: EditorSession.BatteryEdit,value: ()->Battery,links: Set<UUID>) = perform {
        repository.saveBattery(session.initial.takeUnless{session.new},value(),session.links,links)
    }
    fun saveAssignment(session: EditorSession.AssignmentEdit,role: EquipmentType,selected: String?,quantity: String,memory: String) = perform {
        val id=requireNotNull(selected){"Choisissez une référence"}
        val q=if(role==EquipmentType.RADIO) 1 else requireNotNull(quantityInput(quantity,true))
        repository.saveAssignment(session.vehicleId,session.initial,UUID.fromString(id),q,
            if(role==EquipmentType.RADIO) quantityInput(memory) else null,expectedType=role)
    }
    fun remove(session: EditorSession.RemoveAssignment) = perform { repository.removeAssignment(session.initial) }
}

fun readableError(e: Exception): String {
    val text=e.message.orEmpty()
    return when {
        "Insufficient" in text || "Owned quantity below" in text || "Too many tracked" in text -> "Stock insuffisant ou quantité inconnue. Aucun changement appliqué."
        "Release linked receivers" in text -> "Retirez d’abord les récepteurs liés à cette radio. Aucun changement appliqué."
        "Receiver requires" in text || "Invalid receiver radio" in text || "Receiver assignments" in text -> "Le récepteur doit utiliser sa radio associée. Aucun changement appliqué."
        "UNIQUE constraint" in text -> "Cette mémoire radio est déjà utilisée par un autre modèle. Aucun changement appliqué."
        "Equipment with history cannot change type" in text -> "La catégorie d’une référence avec historique ne peut pas être changée."
        "Referenced radio must remain" in text -> "Cette radio est liée à des récepteurs ; sa catégorie doit être conservée."
        "FOREIGN KEY" in text -> "Un lien n’est plus valide. Rechargez la fiche. Aucun changement appliqué."
        else -> text.ifBlank { "Enregistrement impossible. Aucun changement appliqué." }
    }
}
fun nullableInput(value: String,original: String?): String? = if(value==original.orEmpty()) original else value
fun quantityInput(value: String,required: Boolean=false): Int? {
    if(value.isBlank() && !required) return null
    require(value.trim().matches(Regex("[0-9]+"))) { "Quantité entière positive ou nulle attendue" }
    return requireNotNull(value.trim().toIntOrNull()){ "Quantité trop grande" }
}
fun decimalInput(value: String): Double? {
    if(value.isBlank()) return null
    val result=value.trim().replace(',','.').toDoubleOrNull()
    require(result!=null && result.isFinite()) { "Nombre décimal invalide" }
    return result
}
