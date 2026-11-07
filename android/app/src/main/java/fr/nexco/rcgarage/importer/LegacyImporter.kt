package fr.nexco.rcgarage.importer

import fr.nexco.rcgarage.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID

internal data class ImportPlan(val vehicles: List<Vehicle>, val equipment: List<Equipment>,
    val assignments: List<EquipmentAssignment>, val bodies: List<BodyShell>,
    val memories: List<RadioMemory>, val sales: List<Sale>)

class ImportPreview internal constructor(
    private val bytes: ByteArray, val sha256: String, val fileName: String,
    val errors: List<String>, val warnings: List<String>, internal val plan: ImportPlan,
) {
    val vehicleCount get() = plan.vehicles.size
    val equipmentCount get() = plan.equipment.size
    val bodyCount get() = plan.bodies.size
    val assignmentCount get() = plan.assignments.size
    val canConfirm get() = errors.isEmpty()
    fun originalBytes(): ByteArray = bytes.copyOf()
}

object LegacyImporter {
    const val MAX_BYTES = 5 * 1024 * 1024
    fun stableId(kind: String, legacyId: String): UUID = runCatching { UUID.fromString(legacyId) }
        .getOrElse { UUID.nameUUIDFromBytes("rc-garage/legacy/$kind/$legacyId".toByteArray(Charsets.UTF_8)) }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun preview(input: ByteArray, fileName: String = "import.json"): ImportPreview {
        val bytes = input.copyOf()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val vehicles = linkedMapOf<UUID, Vehicle>()
        val gear = linkedMapOf<UUID, Equipment>()
        val bodies = linkedMapOf<UUID, BodyShell>()
        val assignments = linkedMapOf<UUID, EquipmentAssignment>()
        val memories = linkedMapOf<UUID, RadioMemory>()
        val sales = linkedMapOf<UUID, Sale>()
        fun error(path: String, message: String) { errors += "$path : $message" }
        fun string(o: JSONObject, k: String, path: String, required: Boolean = false): String? {
            val v = o.opt(k)
            if (v == null || v == JSONObject.NULL) {
                if (required) error(path, "$k requis")
                return null
            }
            if (v !is String) { error(path, "$k doit être du texte"); return null }
            val s = v.trim()
            if (required && s.isBlank()) error(path, "$k requis")
            return s.takeIf { it.isNotEmpty() }
        }
        fun bool(o: JSONObject, k: String, path: String): Boolean? {
            val v = o.opt(k)
            if (v == null || v == JSONObject.NULL) return null
            if (v !is Boolean) { error(path, "$k doit être booléen"); return null }
            return v
        }
        fun integer(o: JSONObject, k: String, path: String): Int? {
            val v = o.opt(k)
            if (v == null || v == JSONObject.NULL) return null
            if (v !is Number || !v.toDouble().isFinite() || v.toDouble() != v.toInt().toDouble() || v.toInt()<0) {
                error(path, "$k doit être un entier positif ou nul"); return null
            }
            return v.toInt()
        }
        fun array(o: JSONObject, k: String, required: Boolean = false): List<JSONObject> {
            val a = o.opt(k)
            if (a == null && !required) return emptyList()
            if (a !is JSONArray) { error(k, "tableau requis"); return emptyList() }
            return (0 until a.length()).mapNotNull { i ->
                (a.opt(i) as? JSONObject).also { if (it == null) error("$k[$i]", "objet requis") }
            }
        }
        fun <T> add(map: MutableMap<UUID,T>, id: UUID, value: T, path: String) {
            if (map.containsKey(id)) {
                if (map[id] != value) error(path, "identifiant dupliqué avec des valeurs différentes")
                else warnings += "$path : doublon identique ignoré"
            } else map[id] = value
        }
        fun status(value: String?): VehicleStatus = when (value?.lowercase()) {
            "monté", "monte", "built" -> VehicleStatus.BUILT
            "à monter", "a monter", "to_build" -> VehicleStatus.TO_BUILD
            "en cours", "in_progress" -> VehicleStatus.IN_PROGRESS
            "en attente de réception", "awaiting_delivery" -> VehicleStatus.AWAITING_DELIVERY
            "préparation compétition", "competition_prep" -> VehicleStatus.COMPETITION_PREP
            "vendu", "sold" -> VehicleStatus.SOLD
            "à vérifier", "a verifier", "to_verify" -> VehicleStatus.TO_VERIFY
            else -> VehicleStatus.UNKNOWN
        }
        fun type(value: String?): EquipmentType? = when (value?.lowercase()) {
            "radio" -> EquipmentType.RADIO
            "récepteur", "recepteur", "receiver" -> EquipmentType.RECEIVER
            "esc" -> EquipmentType.ESC
            "moteur", "motor" -> EquipmentType.MOTOR
            "servo" -> EquipmentType.SERVO
            else -> null
        }
        try {
            require(bytes.size <= MAX_BYTES) { "Fichier trop volumineux (maximum 5 Mio)" }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            var depth=0
            var quoted=false
            var escaped=false
            for(c in text) {
                if(quoted) {
                    if(escaped) escaped=false
                    else if(c=='\\') escaped=true
                    else if(c=='"') quoted=false
                } else when(c) {
                    '"' -> quoted=true
                    '{','[' -> { depth++; require(depth<=64) { "JSON imbriqué trop profondément" } }
                    '}',']' -> depth--
                }
            }
            val tokener = JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject ?: error("Objet JSON requis")
            require(tokener.nextClean() == '\u0000') { "Contenu supplémentaire après le JSON" }
            val equipmentRows = array(root, "gear", true)
            val modelRows = array(root, "models", true)
            equipmentRows.forEachIndexed { i, o ->
                val path = "gear[$i]"
                val legacy = string(o,"id",path,true) ?: return@forEachIndexed
                val id = stableId("equipment", legacy)
                val t = type(string(o,"type",path,true))
                if (t == null) { error(path,"type inconnu"); return@forEachIndexed }
                val name = string(o,"name",path,true) ?: return@forEachIndexed
                val quantity = integer(o,"quantity",path)
                if (quantity == null) warnings += "$path : quantité inconnue, affectations actives bloquées"
                add(gear,id,Equipment(id,t,string(o,"brand",path),name,quantity,
                    string(o,"inventoryId",path),canonical(o),string(o,"notes",path),
                    linkedRadioId=string(o,"radioId",path)?.let { stableId("equipment",it) }),path)
            }
            modelRows.forEachIndexed { i, o ->
                val path = "models[$i]"
                val legacy = string(o,"id",path,true) ?: return@forEachIndexed
                val id = stableId("vehicle",legacy)
                val brand = string(o,"brand",path,true) ?: return@forEachIndexed
                val name = string(o,"model",path,true) ?: return@forEachIndexed
                val rawStatus = string(o,"status",path)
                val state = status(rawStatus)
                if (state == VehicleStatus.UNKNOWN) warnings += "$path : statut inconnu conservé"
                val saleId = if (state == VehicleStatus.SOLD) stableId("sale",legacy) else null
                val originals = o.opt("originalElectronics")
                if (originals != null && originals != JSONObject.NULL && originals !is JSONObject)
                    error(path,"originalElectronics doit être un objet")
                val vehicle = Vehicle(id,brand,name,string(o,"chassis",path),string(o,"usage",path),string(o,"category",path),string(o,"scale",path),
                    state,rawStatus,bool(o,"rtr",path),bool(o,"rtrOverride",path),
                    (originals as? JSONObject)?.let(::canonical),string(o,"notes",path),saleId=saleId)
                add(vehicles,id,vehicle,path)
                if (saleId != null) add(sales,saleId,Sale(saleId,id,snapshotJson=canonical(o)),path)
                for ((key,role) in listOf("radio" to EquipmentType.RADIO,"receiver" to EquipmentType.RECEIVER,
                    "esc" to EquipmentType.ESC,"motor" to EquipmentType.MOTOR,"servo" to EquipmentType.SERVO)) {
                    val ref = string(o,key,path) ?: continue
                    val eq = stableId("equipment",ref)
                    val aid = stableId("assignment","$legacy/$key/$ref")
                    add(assignments,aid,EquipmentAssignment(aid,id,eq,role,isActive=state!=VehicleStatus.SOLD),path)
                }
                val memory = string(o,"memory",path)
                if (memory != null) {
                    val slot = memory.toIntOrNull()
                    val radio = string(o,"radio",path)
                    if (slot == null || slot<1 || radio == null) warnings += "$path : mémoire radio non exploitable, original conservé"
                    else {
                        val mid = stableId("memory",legacy)
                        add(memories,mid,RadioMemory(mid,stableId("equipment",radio),slot,id,
                            string(o,"receiver",path)?.let { stableId("equipment",it) }),path)
                    }
                }
                val kit = o.opt("boxBody")
                if (kit != null && kit != JSONObject.NULL && kit !is JSONObject) error(path,"boxBody doit être un objet")
                if (kit is JSONObject && bool(kit,"enabled",path)==true) {
                    val bid=stableId("body","kit/$legacy")
                    add(bodies,bid,BodyShell(bid,id,id,true,string(kit,"brand",path),string(kit,"model",path)),path)
                }
                val inline = o.opt("bodies")
                if (inline != null && inline !is JSONArray) error(path,"bodies doit être un tableau")
                if (inline is JSONArray) for (j in 0 until inline.length()) {
                    val b = inline.opt(j)
                    val obj = when (b) { is String -> JSONObject().put("name",b); is JSONObject -> b; else -> null }
                    if (obj == null) { error(path,"carrosserie inline invalide"); continue }
                    val bid = stableId("body","inline/$legacy/$j")
                    add(bodies,bid,parseBody(bid,obj,id,::string),path)
                }
            }
            array(root,"bodyStock").forEachIndexed { i,o ->
                val path="bodyStock[$i]"
                val legacy=string(o,"id",path,true) ?: return@forEachIndexed
                val bid=stableId("body",legacy)
                val owner=string(o,"modelId",path)?.let { stableId("vehicle",it) }
                add(bodies,bid,parseBody(bid,o,owner,::string),path)
            }
            modelRows.forEach { o ->
                val legacy=string(o,"id","models") ?: return@forEach
                val vid=stableId("vehicle",legacy)
                val ids=o.opt("bodyIds")
                if(ids != null && ids !is JSONArray) error("models/$legacy","bodyIds doit être un tableau")
                if(ids is JSONArray) for(i in 0 until ids.length()) {
                    val raw=ids.opt(i)
                    if(raw !is String || raw.isBlank()) { error("models/$legacy","identifiant carrosserie invalide"); continue }
                    val bid=stableId("body",raw)
                    val b=bodies[bid]
                    if(b==null) error("models/$legacy","carrosserie introuvable : $raw")
                    else if(b.currentVehicleId!=null && b.currentVehicleId!=vid) error("models/$legacy","carrosserie affectée à deux véhicules")
                    else bodies[bid]=b.copy(currentVehicleId=vid)
                }
            }
            assignments.values.forEach { a ->
                val e=gear[a.equipmentId]
                if(e==null) error("affectation/${a.id}","équipement introuvable")
                else if(e.type!=a.role) error("affectation/${a.id}","type incompatible")
            }
            gear.values.forEach { e ->
                if(e.linkedRadioId!=null) {
                    if(e.type!=EquipmentType.RECEIVER || gear[e.linkedRadioId]?.type!=EquipmentType.RADIO)
                        error("stock/${e.id}","radio associée introuvable ou incompatible")
                    assignments.values.filter { it.equipmentId==e.id && it.isActive }.forEach { receiver ->
                        if(assignments.values.none { it.vehicleId==receiver.vehicleId && it.equipmentId==e.linkedRadioId && it.isActive })
                            error("stock/${e.id}","récepteur affecté sans sa radio associée")
                    }
                }
                val active=assignments.values.filter { it.equipmentId==e.id && it.isActive }
                val needed=if(e.type==EquipmentType.RADIO && active.isNotEmpty()) 1 else active.sumOf { it.quantity }
                if(needed>0 && (e.ownedQuantity==null || needed>e.ownedQuantity)) error("stock/${e.id}","quantité insuffisante ou inconnue")
            }
            bodies.values.forEach { b ->
                if(b.currentVehicleId!=null && !vehicles.containsKey(b.currentVehicleId)) error("carrosserie/${b.id}","véhicule introuvable")
            }
            val slots=mutableSetOf<Pair<UUID,Int>>()
            memories.values.forEach { m -> if(!slots.add(m.radioId to m.slot)) error("mémoire/${m.id}","emplacement radio dupliqué") }
            val known=setOf("models","gear","bodyStock","kitDefaultsVersion")
            root.keys().forEach { if(it !in known) warnings += "Rubrique $it non interprétée ; original conservé" }
        } catch (e: Exception) { error("JSON",e.message ?: "fichier invalide") }
        return ImportPreview(bytes,hash(bytes),fileName,errors.toList(),warnings.toList(),
            ImportPlan(vehicles.values.toList(),gear.values.toList(),assignments.values.toList(),
                bodies.values.toList(),memories.values.toList(),sales.values.toList()))
    }
    private fun parseBody(id: UUID,o: JSONObject,owner: UUID?,read: (JSONObject,String,String,Boolean)->String?): BodyShell =
        BodyShell(id,currentVehicleId=owner,brand=read(o,"brand","body/$id",false),name=read(o,"name","body/$id",false),
            reference=read(o,"reference","body/$id",false),condition=read(o,"condition","body/$id",false),
            decoration=read(o,"decoration","body/$id",false),notes=read(o,"notes","body/$id",false),
            photo=read(o,"photo","body/$id",false),compatible=read(o,"compatible","body/$id",false))

    internal fun canonical(value: Any?): String = when(value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it)+":"+canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
