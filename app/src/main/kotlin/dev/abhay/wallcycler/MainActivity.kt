package dev.abhay.wallcycler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            MaterialExpressiveTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                motionScheme = MotionScheme.expressive(),
            ) {
                CyclerScreen()
            }
        }
    }
}

private data class FolderInfo(val name: String?, val imageCount: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CyclerScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var folder by remember { mutableStateOf(settings.folder) }
    var frequency by remember { mutableStateOf(settings.frequency) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var folderInfo by remember { mutableStateOf<FolderInfo?>(null) }

    LaunchedEffect(folder) {
        val tree = folder ?: return@LaunchedEffect
        folderInfo = withContext(Dispatchers.IO) {
            runCatching {
                FolderInfo(WallpaperChanger.folderName(context, tree), WallpaperChanger.listImages(context, tree).size)
            }.getOrNull()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        folder?.takeIf { it != uri }?.let {
            runCatching { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        settings.folder = uri
        folder = uri
        folderInfo = null
        if (enabled) CycleWorker.schedule(context, frequency, replace = false)
    }

    val nextChange by remember {
        workManager.getWorkInfosForUniqueWorkFlow(CycleWorker.PERIODIC)
            .map { infos -> infos.firstOrNull { !it.state.isFinished }?.nextScheduleTimeMillis }
    }.collectAsStateWithLifecycle(initialValue = null)

    val nowWork by remember {
        workManager.getWorkInfosForUniqueWorkFlow(CycleWorker.NOW).map { it.firstOrNull() }
    }.collectAsStateWithLifecycle(initialValue = null)
    val changing = nowWork?.state?.isFinished == false
    val lastOutcome = nowWork?.takeIf { it.state.isFinished }?.outputData
        ?.getString(CycleWorker.OUTCOME)?.let { name -> Outcome.entries.firstOrNull { it.name == name } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Wallpaper Cycler") },
                subtitle = { Text("A new wallpaper from your folder, now and then") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EnableCard(
                enabled = enabled,
                onChange = { on ->
                    settings.enabled = on
                    enabled = on
                    when {
                        !on -> CycleWorker.cancelSchedule(context)
                        folder != null -> CycleWorker.schedule(context, frequency, replace = true)
                    }
                },
            )

            // Folder
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Folder", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        text = when {
                            folder == null -> "No folder chosen yet"
                            folderInfo == null -> "Reading folder…"
                            else -> "${folderInfo!!.name ?: "Folder"} · ${imageCountText(folderInfo!!.imageCount)}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { pickFolder.launch(folder) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(if (folder == null) "Choose folder" else "Change folder")
                    }
                }
            }

            // Frequency
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Change every", style = MaterialTheme.typography.titleMediumEmphasized)
                    FrequencyButtons(
                        selected = frequency,
                        enabled = enabled && folder != null,
                        onSelect = { choice ->
                            if (choice != frequency) {
                                settings.frequency = choice
                                frequency = choice
                                CycleWorker.schedule(context, choice, replace = true)
                            }
                        },
                    )
                    Text(
                        text = when {
                            !enabled -> "Cycling is off."
                            folder == null -> "Choose a folder to start."
                            nextChange == null -> "Not scheduled."
                            else -> "Next change around " + DateUtils.formatDateTime(
                                context, nextChange!!,
                                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                            ) + "."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Change now
            Button(
                onClick = { scope.launch { CycleWorker.changeNow(context) } },
                enabled = folder != null && !changing,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                if (changing) {
                    LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Change now", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
            lastOutcome?.let { outcome ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = outcomeText(outcome),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (outcome == Outcome.Changed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The on/off switch for scheduled changes, as a large tappable card that fills with colour when on. */
@Composable
private fun EnableCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) colors.primaryContainer else colors.surfaceContainerHighest,
            contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurface,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enable", style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    if (enabled) "Wallpapers change on schedule" else "No scheduled changes",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null, // the whole card toggles
                thumbContent = if (enabled) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FrequencyButtons(selected: Frequency, enabled: Boolean, onSelect: (Frequency) -> Unit) {
    val options = Frequency.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(option.label)
            }
        }
    }
}

private fun imageCountText(count: Int) = when (count) {
    0 -> "no images"
    1 -> "1 image"
    else -> "$count images"
}

private fun outcomeText(outcome: Outcome) = when (outcome) {
    Outcome.Changed -> "Wallpaper changed."
    Outcome.NoFolder -> "Choose a folder first."
    Outcome.NoOtherImage -> "No other image in the folder."
    Outcome.NoAccess -> "Lost access to the folder. Choose it again."
    Outcome.Failed -> "Couldn't set the wallpaper."
}
