package fr.nexco.rcgarage.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import fr.nexco.rcgarage.collection.*
import fr.nexco.rcgarage.data.*
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CollectionScreen(onHome: ()->Unit,vm: CollectionViewModel=viewModel()) {
    val state by vm.state.collectAsState()
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val root=entry?.destination?.route
    val topLevel=root==null || root in listOf("collection","stock","bodies","batteries")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            for((route,label) in listOf("collection" to "Collection","stock" to "Stocks","bodies" to "Carrosseries","batteries" to "Batteries")) {
                TextButton(onClick={nav.navigate(route){popUpTo("collection");launchSingleTop=true}}){Text(label)}
            }
        }
        TextButton(onClick={if(topLevel) onHome() else nav.popBackStack()}) {
            Text(if(topLevel) "Retour à l’accueil" else "Retour")
        }
        vm.message?.let { Text(it,Modifier.padding(horizontal=16.dp)) }
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            state.error!=null -> Column(Modifier.padding(16.dp)) {
                Text(state.error ?: "Lecture impossible",color=MaterialTheme.colorScheme.error)
                Button(onClick=vm::retry){Text("Réessayer")}
            }
            else -> {
                val data=state.data
                NavHost(nav,startDestination="collection",modifier=Modifier.weight(1f)) {
                    composable("collection") { VehiclesList(data,vm){nav.navigate("vehicle/$it")} }
                    composable("vehicle/{id}") { e ->
                        val v=data.vehicles.find{it.id.toString()==e.arguments?.getString("id")}
                        if(v==null) Missing() else VehicleDetail(v,data,vm,{nav.navigate("equipment/$it")},{nav.navigate("body/$it")})
                    }
                    composable("stock") { EquipmentList(data,vm){nav.navigate("equipment/$it")} }
                    composable("equipment/{id}") { e ->
                        val item=data.equipment.find{it.id.toString()==e.arguments?.getString("id")}
                        if(item==null) Missing() else EquipmentDetail(item,data,vm){nav.navigate("vehicle/$it")}
                    }
                    composable("bodies") { BodiesList(data,vm){nav.navigate("body/$it")} }
                    composable("body/{id}") { e ->
                        val item=data.bodies.find{it.id.toString()==e.arguments?.getString("id")}
                        if(item==null) Missing() else BodyDetail(item,data,vm)
                    }
                    composable("batteries") { BatteriesList(data,vm){nav.navigate("battery/$it")} }
                    composable("battery/{id}") { e ->
                        val item=data.batteries.find{it.id.toString()==e.arguments?.getString("id")}
                        if(item==null) Missing() else BatteryDetail(item,data,vm)
                    }
                }
                CollectionEditor(vm,data)
            }
        }
    }
}

@Composable private fun Missing() { Text("Fiche introuvable. Revenez à la liste.",Modifier.padding(24.dp)) }
@Composable private fun Empty(text: String) { Text(text,Modifier.padding(vertical=16.dp)) }
@Composable private fun Label(name: String,value: String?) { Text("$name : ${value?.takeIf{it.isNotEmpty()} ?: "Non renseigné"}") }
@Composable private fun Details(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable private fun Record(title: String,subtitle: String,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle)
        }
    }
}

@Composable
private fun VehiclesList(data: CollectionData,vm: CollectionViewModel,open: (UUID)->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<VehicleStatus?>(null) }
    val rows=data.filteredVehicles(search,filter)
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Collection — ${data.vehicles.size} modèles",style=MaterialTheme.typography.headlineSmall)
        Input("Rechercher un modèle",search,{search=it})
        Choice("Statut",filter,listOf<VehicleStatus?>(null)+VehicleStatus.entries,{it?.label() ?: "Tous"},{filter=it})
        Button(onClick={vm.edit(EditorSession.VehicleEdit(Vehicle(UUID.randomUUID(),"",""),true))}){Text("Ajouter un modèle")}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.weight(1f).testTag("vehicles-list")) {
            if(rows.isEmpty()) item { Empty(if(data.vehicles.isEmpty()) "Aucun modèle. Importez une sauvegarde ou ajoutez un modèle." else "Aucun résultat.") }
            items(rows,key={it.id}) { v -> Record("${v.brand} ${v.name}","${v.status.label()} · ${v.chassis ?: "Châssis non renseigné"}"){open(v.id)} }
        }
    }
}

@Composable
private fun VehicleDetail(v: Vehicle,data: CollectionData,vm: CollectionViewModel,openEquipment: (UUID)->Unit,openBody: (UUID)->Unit) = Details {
    Text("${v.brand} ${v.name}",style=MaterialTheme.typography.headlineSmall)
    if(v.archived) Text("Archive vendue — lecture seule. Aucune vente/restauration à cette étape.")
    else Button(onClick={vm.edit(EditorSession.VehicleEdit(v))}){Text("Modifier le modèle")}
    Label("Châssis",v.chassis);Label("Statut",v.legacyStatus ?: v.status.label());Label("Usage",v.usage)
    Label("Configuration",when(v.isRtr){true->"RTR";false->"Kit / non RTR";null->"Inconnue"})
    Label("Notes",v.notes)
    Text("Carrosseries",style=MaterialTheme.typography.titleMedium)
    val bodies=data.bodies.filter{it.currentVehicleId==v.id}
    if(bodies.isEmpty()) Text("Aucune carrosserie renseignée")
    bodies.forEach { b ->
        val provenance=when {
            b.isOriginal==true && b.originVehicleId==v.id -> "D’origine du modèle"
            b.isOriginal==true -> "Origine : ${data.vehicles.find{it.id==b.originVehicleId}?.name ?: "Non renseignée"}"
            else -> "Indépendante / provenance non renseignée"
        }
        Record(listOfNotNull(b.brand,b.name,b.reference).joinToString(" "),provenance){openBody(b.id)}
    }
    Text("Électronique",style=MaterialTheme.typography.titleMedium)
    if(v.originElectronicsVisible) Text("Origine — les détails personnels sont masqués, les affectations enregistrées sont conservées.")
    else {
        val active=data.assignments.filter{it.vehicleId==v.id && it.isActive}
        if(active.isEmpty()) Text("Aucun équipement affecté")
        active.forEach { a -> AssignmentCard(a,data,vm,editable=!v.archived,openEquipment=openEquipment) }
        if(!v.archived) Button(onClick={vm.edit(EditorSession.AssignmentEdit(v.id))}){Text("Ajouter un équipement")}
    }
    Text("Batteries compatibles",style=MaterialTheme.typography.titleMedium)
    val compatible=data.compatibility.filter{it.vehicleId==v.id}.map{it.batteryId}
    data.batteries.filter{it.id in compatible}.forEach { Label(it.brand ?: "Batterie",it.reference) }
    if(compatible.isEmpty()) Text("Aucune compatibilité renseignée")
    if(v.archived) {
        data.sales.filter{it.vehicleId==v.id}.forEach { sale ->
            Text(if(sale.historyKnown) "Historique de vente conservé" else "Historique de vente inconnu — aucune vente complète déduite")
            Text(sale.snapshotJson)
        }
    }
}

@Composable
private fun AssignmentCard(a: EquipmentAssignment,data: CollectionData,vm: CollectionViewModel,editable: Boolean,openEquipment: ((UUID)->Unit)?) {
    val eq=data.equipment.find{it.id==a.equipmentId}
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("${a.role.label()} : ${eq?.reference ?: "Référence introuvable"}")
            Text(if(a.role==EquipmentType.RADIO) "Association partagée" else "Quantité affectée : ${a.quantity}")
            val memory=data.memories.find{it.vehicleId==a.vehicleId && it.radioId==a.equipmentId}
            if(a.role==EquipmentType.RADIO) Text("Mémoire : ${memory?.slot?.toString() ?: "Non renseignée"}")
            if(openEquipment!=null) TextButton(onClick={openEquipment(a.equipmentId)}){Text("Voir l’équipement")}
            if(editable) {
                TextButton(onClick={vm.edit(EditorSession.AssignmentEdit(a.vehicleId,a,memory?.slot))}){Text("Réaffecter / modifier")}
                TextButton(onClick={vm.edit(EditorSession.RemoveAssignment(a))}){Text("Retirer l’affectation")}
            }
        }
    }
}

@Composable
private fun EquipmentList(data: CollectionData,vm: CollectionViewModel,open: (UUID)->Unit) {
    var search by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf<EquipmentType?>(null)}
    val rows=data.equipment.filter{(category==null || it.type==category) && listOfNotNull(it.brand,it.reference,it.inventoryLabel).any{t->t.contains(search,true)}}.sortedBy{it.reference}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Équipements et stocks",style=MaterialTheme.typography.headlineSmall)
        Input("Rechercher un équipement",search,{search=it})
        Choice("Catégorie",category,listOf<EquipmentType?>(null)+EquipmentType.entries,{it?.label() ?: "Toutes"},{category=it})
        Button(onClick={vm.edit(EditorSession.EquipmentEdit(Equipment(UUID.randomUUID(),category ?: EquipmentType.ESC,reference=""),true))}){Text("Ajouter un équipement")}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(rows.isEmpty()) item{Empty("Aucun équipement dans cette sélection.")}
            items(rows,key={it.id}){e->Record(listOfNotNull(e.brand,e.reference).joinToString(" "),"${e.type.label()} · possédé : ${e.ownedQuantity?.toString() ?: "inconnu"} · disponible : ${data.available(e)?.toString() ?: "inconnu"}"){open(e.id)}}
        }
    }
}

@Composable
private fun EquipmentDetail(e: Equipment,data: CollectionData,vm: CollectionViewModel,openVehicle: (UUID)->Unit) = Details {
    Text(listOfNotNull(e.brand,e.reference).joinToString(" "),style=MaterialTheme.typography.headlineSmall)
    Button(onClick={vm.edit(EditorSession.EquipmentEdit(e))}){Text("Modifier l’équipement")}
    Label("Catégorie",e.type.label());Label("Quantité possédée",e.ownedQuantity?.toString() ?: "Inconnue")
    if(e.type==EquipmentType.RADIO) Text("${data.users(e.id).size} modèles associés — aucune consommation de stock physique")
    else Text("Quantité affectée : ${data.assigned(e.id).sumOf{it.quantity.toLong()}}")
    Label("Quantité disponible",data.available(e)?.toString() ?: "Inconnue")
    Label("Identifiant / lot",e.inventoryLabel);Label("Notes",e.notes)
    if(e.linkedRadioId!=null) Label("Radio associée",data.equipment.find{it.id==e.linkedRadioId}?.reference)
    Text("Modèles utilisateurs",style=MaterialTheme.typography.titleMedium)
    if(data.users(e.id).isEmpty()) Text("Aucun modèle associé")
    data.users(e.id).forEach{v->TextButton(onClick={openVehicle(v.id)}){Text("${v.brand} ${v.name}")}}
    data.assigned(e.id).forEach { a ->
        val v=data.vehicles.find{it.id==a.vehicleId}
        Text(v?.let{"${it.brand} ${it.name}"} ?: "Modèle introuvable")
        AssignmentCard(a,data,vm,editable=v!=null && !v.archived && !v.originElectronicsVisible,openEquipment=null)
    }
    Text("Historique des affectations",style=MaterialTheme.typography.titleMedium)
    val history=data.assignments.filter{it.equipmentId==e.id}.sortedByDescending{it.createdAt ?: Long.MIN_VALUE}
    if(history.isEmpty()) Text("Aucune affectation enregistrée")
    history.forEach { a ->
        val v=data.vehicles.find{it.id==a.vehicleId}
        Text("${if(a.isActive) "Active" else "Retirée"} · ${v?.name ?: a.vehicleId} · quantité ${a.quantity} · ${if(a.createdAt==null) "date inconnue" else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(a.createdAt).atZone(ZoneId.systemDefault()))}")
    }
}

@Composable
private fun BodiesList(data: CollectionData,vm: CollectionViewModel,open: (UUID)->Unit) {
    var search by rememberSaveable{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Carrosseries",style=MaterialTheme.typography.headlineSmall)
        Input("Rechercher une carrosserie",search,{search=it})
        Button(onClick={vm.edit(EditorSession.BodyEdit(BodyShell(UUID.randomUUID()),true))}){Text("Ajouter une carrosserie")}
        val rows=data.bodies.filter{listOfNotNull(it.brand,it.name,it.reference).any{t->t.contains(search,true)}}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(rows.isEmpty()) item{Empty("Aucune carrosserie.")}
            items(rows,key={it.id}){b->Record(listOfNotNull(b.brand,b.name,b.reference).joinToString(" "),if(b.currentVehicleId==null) "Stock indépendant" else "Montée sur un modèle"){open(b.id)}}
        }
    }
}
@Composable
private fun BodyDetail(b: BodyShell,data: CollectionData,vm: CollectionViewModel) = Details {
    Text(listOfNotNull(b.brand,b.name,b.reference).joinToString(" "),style=MaterialTheme.typography.headlineSmall)
    val owner=data.vehicles.find{it.id==b.currentVehicleId}
    if(owner?.archived!=true) Button(onClick={vm.edit(EditorSession.BodyEdit(b))}){Text("Modifier la carrosserie")}
    else Text("Carrosserie d’une archive — lecture seule")
    Label("État",b.condition);Label("Décoration",b.decoration);Label("Notes",b.notes)
    Label("Modèle actuel",owner?.let{"${it.brand} ${it.name}"} ?: "Stock indépendant")
    Label("Origine",data.vehicles.find{it.id==b.originVehicleId}?.let{"${it.brand} ${it.name}"} ?: "Inconnue")
    Label("Châssis compatibles",b.compatible);Label("Photo (URL conservée)",b.photo)
}

@Composable
private fun BatteriesList(data: CollectionData,vm: CollectionViewModel,open: (UUID)->Unit) {
    var search by rememberSaveable{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Batteries",style=MaterialTheme.typography.headlineSmall)
        Input("Rechercher une batterie",search,{search=it})
        Button(onClick={vm.edit(EditorSession.BatteryEdit(Battery(UUID.randomUUID()),emptySet(),true))}){Text("Ajouter une batterie")}
        val rows=data.batteries.filter{listOfNotNull(it.brand,it.reference,it.chemistry,it.connector).any{t->t.contains(search,true)}}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(rows.isEmpty()) item{Empty("Aucune batterie renseignée.")}
            items(rows,key={it.id}){b->Record(listOfNotNull(b.brand,b.reference).joinToString(" "),"Quantité possédée : ${b.ownedQuantity?.toString() ?: "inconnue"}"){open(b.id)}}
        }
    }
}
@Composable
private fun BatteryDetail(b: Battery,data: CollectionData,vm: CollectionViewModel) = Details {
    Text(listOfNotNull(b.brand,b.reference).joinToString(" "),style=MaterialTheme.typography.headlineSmall)
    val links=data.compatibility.filter{it.batteryId==b.id}.map{it.vehicleId}.toSet()
    Button(onClick={vm.edit(EditorSession.BatteryEdit(b,links))}){Text("Modifier la batterie")}
    Label("Chimie",b.chemistry);Label("Capacité (mAh)",b.capacityMah?.toString());Label("Cellules",b.cells?.toString())
    Label("Tension (V)",b.voltage?.toString());Label("Taux C",b.cRating?.toString());Label("Connecteur",b.connector)
    Label("Quantité possédée",b.ownedQuantity?.toString() ?: "Inconnue")
    Text("Modèles compatibles — sans réservation de stock",style=MaterialTheme.typography.titleMedium)
    if(links.isEmpty()) Text("Aucune compatibilité renseignée")
    data.vehicles.filter{it.id in links}.forEach{Text("${it.brand} ${it.name}")}
}
