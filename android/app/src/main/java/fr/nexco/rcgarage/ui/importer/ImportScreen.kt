package fr.nexco.rcgarage.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.nexco.rcgarage.R

@Composable
fun ImportScreen(onBack: () -> Unit, model: ImportViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val counts by model.counts.collectAsState()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) model.load(uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_title),style=MaterialTheme.typography.headlineMedium)
        Text(counts)
        Text(stringResource(R.string.import_explanation))
        Button(enabled=!state.busy,onClick={picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))}) {
            Text(stringResource(R.string.import_choose))
        }
        if(state.busy) CircularProgressIndicator()
        state.preview?.let { preview ->
            Text(stringResource(R.string.import_counts,preview.vehicleCount,preview.equipmentCount,preview.bodyCount))
            Text(stringResource(R.string.import_fingerprint,preview.sha256))
            preview.errors.forEach { Text(it,color=MaterialTheme.colorScheme.error) }
            preview.warnings.forEach { Text(it) }
            if(!state.done) {
                Button(enabled=preview.canConfirm && !state.busy,onClick=model::confirm) {
                    Text(stringResource(R.string.import_confirm))
                }
                TextButton(enabled=!state.busy,onClick=model::cancel) { Text(stringResource(R.string.import_cancel)) }
            }
        }
        state.message?.let { Text(it) }
        TextButton(enabled=!state.busy,onClick=onBack) { Text(stringResource(R.string.back_home)) }
    }
}
