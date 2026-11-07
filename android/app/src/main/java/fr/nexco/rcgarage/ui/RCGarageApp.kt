package fr.nexco.rcgarage.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.nexco.rcgarage.R
import fr.nexco.rcgarage.ui.importer.ImportScreen
import fr.nexco.rcgarage.ui.theme.RCGarageTheme
import fr.nexco.rcgarage.ui.theme.ThemeMode

@Composable
fun RCGarageApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    }
    var mode by remember {
        mutableStateOf(ThemeMode.entries.firstOrNull {
            it.name == preferences.getString("theme", null)
        } ?: ThemeMode.SYSTEM)
    }
    val navController = rememberNavController()
    RCGarageTheme(mode) {
        Scaffold { insets ->
            NavHost(navController, startDestination = "home", modifier = Modifier.padding(insets)) {
                composable("home") {
                    HomeScreen(onAppearance = { navController.navigate("appearance") { launchSingleTop = true } },
                        onImport = { navController.navigate("import") { launchSingleTop = true } })
                }
                composable("import") { ImportScreen(onBack = { navController.popBackStack() }) }
                composable("appearance") {
                    AppearanceScreen(mode, onModeChange = {
                        mode = it
                        preferences.edit().putString("theme", it.name).apply()
                    }, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onAppearance: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.home_description))
        Text(stringResource(R.string.home_placeholder))
        Button(onClick = onImport) { Text(stringResource(R.string.import_title)) }
        Button(onClick = onAppearance) { Text(stringResource(R.string.settings)) }
    }
}

@Composable
private fun AppearanceScreen(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { option ->
                val label = when (option) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = option == mode, role = Role.RadioButton,
                        onClick = { onModeChange(option) },
                    ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == mode, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Button(onClick = onBack) { Text(stringResource(R.string.back_home)) }
    }
}
