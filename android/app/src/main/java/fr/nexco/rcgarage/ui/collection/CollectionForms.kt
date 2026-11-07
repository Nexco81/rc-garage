package fr.nexco.rcgarage.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.nexco.rcgarage.collection.*
import fr.nexco.rcgarage.data.*
import java.util.UUID

@Composable
internal fun Input(label: String,value: String,change: (String)->Unit,suggestions: List<String> = emptyList(),lines: Int=1) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth().testTag("field:$label"),
        singleLine=lines==1,minLines=lines)
    if(value.isNotBlank()) {
        val matches=suggestions.filter{it.contains(value,true) && !it.equals(value,true)}.take(6)
        for(s in matches) TextButton(onClick={change(s)}) { Text(s) }
    } else for(s in suggestions.take(6)) TextButton(onClick={change(s)}) { Text(s) }
}

@Composable
private fun QuantityField(value: String,change: (String)->Unit) {
    Input("Quantité possédée — vide = inconnue",value,change)
    val quantity=value.trim().toIntOrNull()
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedButton(enabled=quantity!=null && quantity>0,onClick={change((requireNotNull(quantity)-1).toString())}) { Text("−") }
        OutlinedButton(enabled=quantity!=null && quantity>=0 && quantity<Int.MAX_VALUE,onClick={change((requireNotNull(quantity)+1).toString())}) { Text("+") }
    }
}

@Composable
internal fun <T> Choice(label: String,value: T,options: List<T>,name: (T)->String,choose: (T)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) { Text("$label : ${name(value)}") }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach { option -> DropdownMenuItem(text={Text(name(option))},onClick={choose(option);expanded=false}) }
        }
    }
}

@Composable
internal fun EditorFrame(title: String,vm: CollectionViewModel,save: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=vm::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
                vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(enabled=!vm.busy,onClick=save){Text("Enregistrer")}
                    TextButton(enabled=!vm.busy,onClick=vm::dismiss){Text("Annuler")}
                }
            }
        }
    }
}

@Composable
internal fun CollectionEditor(vm: CollectionViewModel,data: CollectionData) {
    when(val session=vm.editor) {
        is EditorSession.VehicleEdit -> VehicleForm(session,vm,data)
        is EditorSession.EquipmentEdit -> EquipmentForm(session,vm,data)
        is EditorSession.BodyEdit -> BodyForm(session,vm,data)
        is EditorSession.BatteryEdit -> BatteryForm(session,vm,data)
        is EditorSession.AssignmentEdit -> AssignmentForm(session,vm,data)
        is EditorSession.RemoveAssignment -> AlertDialog(onDismissRequest=vm::dismiss,
            title={Text("Retirer cette affectation ?")},text={Column {Text("La ligne reste dans l’historique. Le stock physique disponible est recalculé ; aucune référence n’est supprimée.");vm.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},
            confirmButton={TextButton(enabled=!vm.busy,onClick={vm.remove(session)}){Text("Confirmer le retrait")}},
            dismissButton={TextButton(enabled=!vm.busy,onClick=vm::dismiss){Text("Annuler")}})
        null -> Unit
    }
}

@Composable
private fun VehicleForm(s: EditorSession.VehicleEdit,vm: CollectionViewModel,data: CollectionData) {
    val v=s.initial
    var brand by rememberSaveable(v.id.toString()){mutableStateOf(v.brand)}
    var name by rememberSaveable(v.id.toString()){mutableStateOf(v.name)}
    var chassis by rememberSaveable(v.id.toString()){mutableStateOf(v.chassis.orEmpty())}
    var usage by rememberSaveable(v.id.toString()){mutableStateOf(v.usage.orEmpty())}
    var notes by rememberSaveable(v.id.toString()){mutableStateOf(v.notes.orEmpty())}
    var status by rememberSaveable(v.id.toString()){mutableStateOf(v.status)}
    var rtr by rememberSaveable(v.id.toString()){mutableStateOf(v.isRtr)}
    var custom by rememberSaveable(v.id.toString()){mutableStateOf(v.electronicsCustomized)}
    EditorFrame(if(s.new) "Nouveau modèle" else "Modifier le modèle",vm,save={
        vm.saveVehicle(s,VehicleFields(brand,name,nullableInput(chassis,v.chassis),status,
            nullableInput(usage,v.usage),nullableInput(notes,v.notes),rtr,custom))
    }) {
        Input("Marque",brand,{brand=it},data.vehicleBrands())
        Input("Nom / référence",name,{name=it})
        Input("Châssis",chassis,{chassis=it},data.chassisSuggestions(brand))
        Choice("Statut",status,VehicleStatus.entries.filter{it!=VehicleStatus.SOLD},{it.label()},{status=it})
        Input("Usage",usage,{usage=it},data.vehicles.mapNotNull{it.usage}.distinct())
        Choice("RTR",rtr,listOf<Boolean?>(null,false,true),{when(it){true->"RTR";false->"Kit / non RTR";null->"Inconnu"}},{rtr=it})
        Choice("Électronique personnelle",custom,listOf<Boolean?>(null,false,true),{when(it){true->"Activée";false->"Désactivée";null->"Inconnue"}},{custom=it})
        Text("Ces choix masquent ou réaffichent les équipements sans effacer les affectations ou les données d’origine.")
        Input("Notes",notes,{notes=it},lines=4)
    }
}

@Composable
private fun EquipmentForm(s: EditorSession.EquipmentEdit,vm: CollectionViewModel,data: CollectionData) {
    val e=s.initial
    var brand by rememberSaveable(e.id.toString()){mutableStateOf(e.brand.orEmpty())}
    var reference by rememberSaveable(e.id.toString()){mutableStateOf(e.reference)}
    var quantity by rememberSaveable(e.id.toString()){mutableStateOf(e.ownedQuantity?.toString().orEmpty())}
    var label by rememberSaveable(e.id.toString()){mutableStateOf(e.inventoryLabel.orEmpty())}
    var notes by rememberSaveable(e.id.toString()){mutableStateOf(e.notes.orEmpty())}
    var type by rememberSaveable(e.id.toString()){mutableStateOf(e.type)}
    var linked by rememberSaveable(e.id.toString()){mutableStateOf(e.linkedRadioId?.toString())}
    EditorFrame(if(s.new) "Nouvel équipement" else "Modifier l’équipement",vm,save={vm.saveEquipment(s) {
        e.copy(brand=nullableInput(brand,e.brand),reference=reference,type=type,
            ownedQuantity=quantityInput(quantity),inventoryLabel=nullableInput(label,e.inventoryLabel),
            notes=nullableInput(notes,e.notes),linkedRadioId=linked?.let(UUID::fromString))
    }}) {
        Input("Marque",brand,{brand=it},data.equipmentBrands())
        Input("Nom / référence",reference,{reference=it})
        if(s.new || data.assignments.none{it.equipmentId==e.id}) Choice("Catégorie",type,EquipmentType.entries.toList(),{it.label()},{type=it;if(it!=EquipmentType.RECEIVER) linked=null})
        else Text("Catégorie : ${e.type.label()} (historique conservé)")
        QuantityField(quantity,{quantity=it})
        Input("Identifiant / lot",label,{label=it})
        if(type==EquipmentType.RECEIVER) {
            val radios=data.equipment.filter{it.type==EquipmentType.RADIO}
            Choice("Radio associée",linked,listOf<String?>(null)+radios.map{it.id.toString()},
                {id->radios.find{it.id.toString()==id}?.reference ?: "Aucune / inconnue"},{linked=it})
        }
        if(type==EquipmentType.RADIO) Text("Les associations aux modèles ne consomment pas la quantité physique.")
        Input("Notes",notes,{notes=it},lines=3)
    }
}

@Composable
private fun BodyForm(s: EditorSession.BodyEdit,vm: CollectionViewModel,data: CollectionData) {
    val b=s.initial
    var brand by rememberSaveable(b.id.toString()){mutableStateOf(b.brand.orEmpty())}
    var name by rememberSaveable(b.id.toString()){mutableStateOf(b.name.orEmpty())}
    var ref by rememberSaveable(b.id.toString()){mutableStateOf(b.reference.orEmpty())}
    var condition by rememberSaveable(b.id.toString()){mutableStateOf(b.condition.orEmpty())}
    var decoration by rememberSaveable(b.id.toString()){mutableStateOf(b.decoration.orEmpty())}
    var notes by rememberSaveable(b.id.toString()){mutableStateOf(b.notes.orEmpty())}
    var owner by rememberSaveable(b.id.toString()){mutableStateOf(b.currentVehicleId?.toString())}
    EditorFrame("Carrosserie",vm,save={vm.saveBody(s,b.copy(brand=nullableInput(brand,b.brand),name=nullableInput(name,b.name),
        reference=nullableInput(ref,b.reference),condition=nullableInput(condition,b.condition),decoration=nullableInput(decoration,b.decoration),
        notes=nullableInput(notes,b.notes),currentVehicleId=owner?.let(UUID::fromString)))}) {
        Input("Marque",brand,{brand=it},data.bodyBrands())
        Input("Nom",name,{name=it});Input("Référence",ref,{ref=it})
        Input("État",condition,{condition=it});Input("Décoration",decoration,{decoration=it})
        Text(if(b.isOriginal==true) "Carrosserie d’origine : provenance conservée" else "Carrosserie indépendante — provenance conservée")
        val vehicles=data.vehicles.filter{!it.archived || it.id==b.currentVehicleId}
        Choice("Modèle actuel",owner,listOf<String?>(null)+vehicles.map{it.id.toString()},
            {id->vehicles.find{it.id.toString()==id}?.let{"${it.brand} ${it.name}"} ?: "Stock indépendant"},{owner=it})
        Input("Notes",notes,{notes=it},lines=3)
    }
}

@Composable
private fun BatteryForm(s: EditorSession.BatteryEdit,vm: CollectionViewModel,data: CollectionData) {
    val b=s.initial
    var brand by rememberSaveable(b.id.toString()){mutableStateOf(b.brand.orEmpty())}
    var ref by rememberSaveable(b.id.toString()){mutableStateOf(b.reference.orEmpty())}
    var chemistry by rememberSaveable(b.id.toString()){mutableStateOf(b.chemistry.orEmpty())}
    var capacity by rememberSaveable(b.id.toString()){mutableStateOf(b.capacityMah?.toString().orEmpty())}
    var cells by rememberSaveable(b.id.toString()){mutableStateOf(b.cells?.toString().orEmpty())}
    var voltage by rememberSaveable(b.id.toString()){mutableStateOf(b.voltage?.toString().orEmpty())}
    var rate by rememberSaveable(b.id.toString()){mutableStateOf(b.cRating?.toString().orEmpty())}
    var connector by rememberSaveable(b.id.toString()){mutableStateOf(b.connector.orEmpty())}
    var quantity by rememberSaveable(b.id.toString()){mutableStateOf(b.ownedQuantity?.toString().orEmpty())}
    var links by rememberSaveable(b.id.toString()){mutableStateOf(s.links.map{it.toString()})}
    EditorFrame("Batterie",vm,save={vm.saveBattery(s,{
        b.copy(brand=nullableInput(brand,b.brand),reference=nullableInput(ref,b.reference),chemistry=nullableInput(chemistry,b.chemistry),
            capacityMah=quantityInput(capacity),cells=quantityInput(cells),voltage=decimalInput(voltage),cRating=decimalInput(rate),
            connector=nullableInput(connector,b.connector),ownedQuantity=quantityInput(quantity))
    },links.map(UUID::fromString).toSet())}) {
        Input("Marque",brand,{brand=it},data.batteryBrands());Input("Nom / référence",ref,{ref=it})
        Input("Chimie",chemistry,{chemistry=it});Input("Capacité (mAh)",capacity,{capacity=it})
        Input("Cellules",cells,{cells=it});Input("Tension (V)",voltage,{voltage=it});Input("Taux C",rate,{rate=it})
        Input("Connecteur",connector,{connector=it});QuantityField(quantity,{quantity=it})
        Text("Modèles compatibles — ces liens ne réservent pas de batterie physique")
        data.vehicles.forEach { v -> Row {
            Checkbox(checked=v.id.toString() in links,enabled=!v.archived,onCheckedChange={checked->links=if(checked) links+v.id.toString() else links-v.id.toString()})
            Text("${v.brand} ${v.name}${if(v.archived) " (archive)" else ""}")
        } }
    }
}

@Composable
private fun AssignmentForm(s: EditorSession.AssignmentEdit,vm: CollectionViewModel,data: CollectionData) {
    var role by rememberSaveable(s.vehicleId.toString(),s.initial?.id.toString()){mutableStateOf(s.initial?.role ?: EquipmentType.ESC)}
    var selected by rememberSaveable(s.vehicleId.toString(),s.initial?.id.toString()){mutableStateOf(s.initial?.equipmentId?.toString())}
    var quantity by rememberSaveable(s.vehicleId.toString(),s.initial?.id.toString()){mutableStateOf(s.initial?.quantity?.toString() ?: "1")}
    var memory by rememberSaveable(s.vehicleId.toString(),s.initial?.id.toString()){mutableStateOf(s.memory?.toString().orEmpty())}
    EditorFrame(if(s.initial==null) "Ajouter une affectation" else "Réaffecter",vm,save={vm.saveAssignment(s,role,selected,quantity,memory)}) {
        if(s.initial==null) Choice("Rôle",role,EquipmentType.entries.toList(),{it.label()},{role=it;selected=null;memory=""})
        else Text(role.label())
        val items=data.equipment.filter{it.type==role}
        Choice("Référence",selected,listOf<String?>(null)+items.map{it.id.toString()},
            {id->items.find{it.id.toString()==id}?.let{e->"${e.brand.orEmpty()} ${e.reference} — disponible : ${data.available(e)?.toString() ?: "inconnu"}"} ?: "Choisir"},
            {if(selected!=it) memory="";selected=it})
        if(items.isEmpty()) Text("Aucune référence dans cette catégorie. Créez-la depuis Stocks.")
        if(role==EquipmentType.RADIO) {
            Text("Association partagée : aucune consommation de stock physique.")
            Input("Mémoire radio (facultative)",memory,{memory=it})
        } else Input("Quantité affectée",quantity,{quantity=it})
    }
}
