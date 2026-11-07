package fr.nexco.rcgarage.ui.importer

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nexco.rcgarage.data.*
import fr.nexco.rcgarage.importer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

data class ImportUiState(val busy: Boolean = false, val preview: ImportPreview? = null,
    val message: String? = null, val done: Boolean = false)

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GarageRepository(GarageDatabase.get(application))
    private val mutable = MutableStateFlow(ImportUiState())
    val state = mutable.asStateFlow()
    val counts = combine(repository.vehicles,repository.equipment,repository.bodies) { v,e,b ->
        "${v.size} modèles · ${e.size} références · ${b.size} carrosseries"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun load(uri: Uri) {
        if(mutable.value.busy) return
        mutable.value=ImportUiState(busy=true)
        viewModelScope.launch {
            try {
                val preview=withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    val bytes=resolver.openInputStream(uri)?.use { stream ->
                        val out=ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val n=stream.read(buffer)
                            if(n<0) break
                            require(out.size()+n<=LegacyImporter.MAX_BYTES) { "Maximum 5 Mio" }
                            out.write(buffer,0,n)
                        }
                        out.toByteArray()
                    } ?: error("Fichier inaccessible")
                    // No filesystem path, permission persistence, or write access is needed.
                    val fileName=runCatching {
                        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                            if(cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: "document.json"
                    LegacyImporter.preview(bytes,fileName)
                }
                mutable.value=ImportUiState(preview=preview)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.value=ImportUiState(message=e.message ?: "Lecture impossible") }
        }
    }
    fun confirm() {
        val old=mutable.value
        val preview=old.preview ?: return
        if(old.busy || old.done || !preview.canConfirm) return
        mutable.value=old.copy(busy=true,message=null)
        viewModelScope.launch {
            val result=withContext(Dispatchers.IO) { repository.confirm(preview) }
            mutable.value=when(result) {
                is ImportOutcome.Success -> old.copy(done=true,message=if(result.alreadyImported)
                    "Fichier déjà importé, aucune modification." else
                    "Import terminé : ${result.vehicles} modèles, ${result.equipment} références, ${result.bodies} carrosseries. ${result.duplicateRows} doublons ignorés.")
                is ImportOutcome.Failure -> old.copy(message=result.errors.joinToString("\n"))
            }
        }
    }
    fun cancel() { if(!mutable.value.busy) mutable.value=ImportUiState() }
}
