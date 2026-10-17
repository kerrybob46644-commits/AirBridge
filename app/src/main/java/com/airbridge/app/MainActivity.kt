package com.airbridge.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airbridge.app.airplay.AirplayDevice
import com.airbridge.app.airplay.RaopSender.LatencyMode

class MainActivity : ComponentActivity() {

    private companion object {
        const val PERM_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private var pendingDevice: AirplayDevice? = null

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val device = pendingDevice ?: return@registerForActivityResult
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, CastService::class.java)
                    .setAction(CastService.ACTION_START)
                    .putExtra(CastService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(CastService.EXTRA_DATA, result.data)
                    .putExtra(CastService.EXTRA_HOST, device.host)
                    .putExtra(CastService.EXTRA_PORT, device.port)
                    .putExtra(CastService.EXTRA_NAME, device.name)
                ContextCompat.startForegroundService(this, intent)
            } else {
                CastController.addLog("Screen/audio capture permission not granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            val perms = ArrayList<String>()
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val localNetworkExists = try {
                packageManager.getPermissionInfo(PERM_LOCAL_NETWORK, 0)
                true
            } catch (e: Exception) {
                false
            }
            if (localNetworkExists &&
                checkSelfPermission(PERM_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(PERM_LOCAL_NETWORK)
            }
            if (perms.isNotEmpty()) {
                requestPermissions(perms.toTypedArray(), 100)
            }
        }
        CastController.loadManual(applicationContext)
        CastController.startDiscovery(applicationContext)
        setContent {
            App(onConnect = { device -> connect(device) })
        }
    }

    private fun connect(device: AirplayDevice) {
        pendingDevice = device
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        if (isFinishing) CastController.stopDiscovery()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(onConnect: (AirplayDevice) -> Unit) {
    val ui by CastController.state.collectAsState()
    val context = LocalContext.current
    var showManual by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (ui.themeMode) {
        ThemeHelper.THEME_LIGHT -> false
        ThemeHelper.THEME_DARK -> true
        else -> systemDark
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        // 同步状态栏图标颜色，保证深色下可见（窗口背景沿用浅色系统主题）。
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                    !darkTheme
            }
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text("AirBridge") }) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showManual = true },
                    icon = { Text("+") },
                    text = { Text(stringResource(R.string.add_manual)) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(ui)
                SettingsCard(ui)
                if (ui.streaming) {
                    VolumeCard(
                        ui = ui,
                        onVolume = { CastController.setVolume(it) },
                        onStop = { CastController.stop(context) }
                    )
                    LogPanel(ui.logs, modifier = Modifier.weight(1f))
                } else {
                    DeviceList(ui, onConnect, onDelete = { CastController.removeManual(it) }, modifier = Modifier.weight(1f))
                    LogPanel(ui.logs, modifier = Modifier.height(140.dp))
                }
            }
        }
    }

    if (showManual) {
        ManualDialog(
            onDismiss = { showManual = false },
            onAdd = { name, ip, port ->
                CastController.addManual(
                    AirplayDevice(name = name, host = ip, port = port, type = CastController.TYPE_MANUAL)
                )
                showManual = false
            }
        )
    }
}

@Composable
private fun SettingsCard(ui: CastController.UiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.latency_network), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.latency_hint), style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                LatencyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ui.latencyMode == mode,
                        onClick = { CastController.setLatencyMode(mode) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        LatencyMode.STABLE -> R.string.latency_stable
                                        LatencyMode.LOW -> R.string.latency_low
                                        LatencyMode.ULTRA -> R.string.latency_ultra
                                        LatencyMode.SAFE -> R.string.latency_safe
                                    }
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
            Text(
                stringResource(R.string.latency_detail),
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.robust), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.robust_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = ui.robustMode,
                    onCheckedChange = { CastController.setRobustMode(it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.alac), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.alac_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = ui.alacOn,
                    onCheckedChange = { CastController.setAlac(it) }
                )
            }
            LanguageRow()
            ThemeRow(selected = ui.themeMode)
        }
    }
}

@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val current = LocaleHelper.getLangCode(context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.language),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = current == LocaleHelper.LANG_ENGLISH,
            onClick = { switchLanguage(context, LocaleHelper.LANG_ENGLISH) },
            label = { Text(stringResource(R.string.language_en)) }
        )
        FilterChip(
            selected = current == LocaleHelper.LANG_CHINESE,
            onClick = { switchLanguage(context, LocaleHelper.LANG_CHINESE) },
            label = { Text(stringResource(R.string.language_zh)) }
        )
    }
}

private fun switchLanguage(context: Context, langCode: String) {
    val activity = context as? Activity ?: return
    LocaleHelper.setLanguage(activity.applicationContext, langCode)
    activity.recreate()
}

@Composable
private fun ThemeRow(selected: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.theme),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selected == ThemeHelper.THEME_SYSTEM,
            onClick = { CastController.setThemeMode(ThemeHelper.THEME_SYSTEM) },
            label = { Text(stringResource(R.string.theme_system)) }
        )
        FilterChip(
            selected = selected == ThemeHelper.THEME_LIGHT,
            onClick = { CastController.setThemeMode(ThemeHelper.THEME_LIGHT) },
            label = { Text(stringResource(R.string.theme_light)) }
        )
        FilterChip(
            selected = selected == ThemeHelper.THEME_DARK,
            onClick = { CastController.setThemeMode(ThemeHelper.THEME_DARK) },
            label = { Text(stringResource(R.string.theme_dark)) }
        )
    }
}

@Composable
private fun StatusCard(ui: CastController.UiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(ui.status, style = MaterialTheme.typography.titleMedium)
            Text(
                if (ui.discovering) {
                    stringResource(R.string.status_scanning, ui.devices.size)
                } else {
                    stringResource(R.string.devices_count, ui.devices.size)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (ui.connecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DeviceList(
    ui: CastController.UiState,
    onConnect: (AirplayDevice) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (AirplayDevice) -> Unit = {}
) {
    if (ui.devices.isEmpty()) {
        Text(stringResource(R.string.no_devices))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(ui.devices, key = { "${it.type}|${it.name}|${it.host}:${it.port}" }) { d ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        Text(d.model ?: "AirPlay", style = MaterialTheme.typography.bodySmall)
                        Text("${d.host}:${d.port}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onConnect(d) }, enabled = !ui.connecting) {
                        Text(stringResource(R.string.connect))
                    }
                    if (d.type == CastController.TYPE_MANUAL || d.txt.isEmpty()) {
                        TextButton(onClick = { onDelete(d) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(ui: CastController.UiState, onVolume: (Float) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.casting), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { CastController.toggleTestTone(context) }) {
                Text(if (ui.testToneOn) stringResource(R.string.test_tone_stop) else stringResource(R.string.test_tone_play))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.volume))
                Slider(
                    value = ui.volume,
                    onValueChange = onVolume,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    valueRange = 0f..100f
                )
                Text("${ui.volume.toInt()}%")
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.stop_casting))
            }
        }
    }
}

@Composable
private fun LogPanel(logs: List<String>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(logs) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.logs), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { CastController.clearLogs() }) {
                    Text(stringResource(R.string.clear_logs))
                }
            }
            Text(
                text = logs.takeLast(40).joinToString("\n").ifEmpty { stringResource(R.string.logs_empty) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun ManualDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, ip: String, port: Int) -> Unit
) {
    val defaultName = stringResource(R.string.default_device_name)
    var name by remember { mutableStateOf(defaultName) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.ip_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = port.toIntOrNull() ?: 7000
                    if (ip.isNotBlank()) onAdd(name.ifBlank { ip }, ip.trim(), p)
                },
                enabled = ip.isNotBlank() && (port.toIntOrNull()?.let { it in 1..65535 } ?: false)
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}