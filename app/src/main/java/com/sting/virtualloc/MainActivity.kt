package com.sting.virtualloc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            VirtuaLocTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
data class UiState(
    val isRunning: Boolean = false,
    val latText: String = "39.9042",
    val lngText: String = "116.4074",
    val devModeEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val showDeveloperDialog: Boolean = false,
    val statusMessage: String = "请输入坐标后点击「开启虚拟定位」"
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun updateLat(text: String) {
        _state.value = _state.value.copy(latText = text)
    }

    fun updateLng(text: String) {
        _state.value = _state.value.copy(lngText = text)
    }

    fun setRunning(running: Boolean) {
        _state.value = _state.value.copy(isRunning = running)
    }

    fun setDevModeEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(devModeEnabled = enabled)
    }

    fun setHasPermission(has: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = has)
    }

    fun setStatus(msg: String) {
        _state.value = _state.value.copy(statusMessage = msg)
    }

    fun showDeveloperDialog() {
        _state.value = _state.value.copy(showDeveloperDialog = true)
    }

    fun hideDeveloperDialog() {
        _state.value = _state.value.copy(showDeveloperDialog = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val vm = remember { MainViewModel() }
    val state by vm.state.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.setHasPermission(granted)
        if (!granted) {
            vm.setStatus("需要位置权限才能使用")
        }
    }

    // Notification permission launcher (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Check permissions on launch
    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        vm.setHasPermission(hasPerm)

        if (!hasPerm) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Check dev mode
    val mockMgr = remember { MockLocationManager(context) }
    LaunchedEffect(state.hasLocationPermission) {
        if (state.hasLocationPermission) {
            vm.setDevModeEnabled(mockMgr.isDeveloperMockEnabled())
        }
    }

    // Dialog: Developer mode instructions
    if (state.showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { vm.hideDeveloperDialog() },
            title = { Text("开启开发者模拟位置", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("步骤 1: 开启开发者选项")
                    Text("设置 → 关于手机 → 连续点击「版本号」7次 → 输入解锁密码 → 开启成功")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("步骤 2: 开启模拟位置")
                    Text("设置 → 系统 → 开发者选项 → 找到「选择模拟位置信息应用」→ 选择 VirtuaLoc")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("步骤 3: 确认权限")
                    Text("若弹出权限请求，点击「允许」即可")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.hideDeveloperDialog()
                    // Open developer settings
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    }
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.hideDeveloperDialog() }) {
                    Text("知道了")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VirtuaLoc 虚拟定位") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    TextButton(onClick = { vm.showDeveloperDialog() }) {
                        Text("帮助")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status card
            val statusColor = if (state.isRunning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isRunning)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.isRunning) "🟢 虚拟定位运行中" else "⚪ 已停止",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Dev mode warning
            if (!state.devModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️ 未检测到模拟位置权限")
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.showDeveloperDialog() }) {
                            Text("开启教程")
                        }
                    }
                }
            }

            // Coordinate inputs
            Text(
                text = "目标坐标",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.latText,
                    onValueChange = { vm.updateLat(it) },
                    label = { Text("纬度 (-90~90)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isRunning
                )
                OutlinedTextField(
                    value = state.lngText,
                    onValueChange = { vm.updateLng(it) },
                    label = { Text("经度 (-180~180)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isRunning
                )
            }

            // Quick location buttons
            Text(
                text = "快速选择",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "北京" to Pair(39.9042, 116.4074),
                    "上海" to Pair(31.2304, 121.4737),
                    "纽约" to Pair(40.7128, -74.0060),
                    "东京" to Pair(35.6762, 139.6503)
                ).forEach { (name, coords) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (!state.isRunning) {
                                vm.updateLat(coords.first.toString())
                                vm.updateLng(coords.second.toString())
                            }
                        },
                        label = { Text(name) },
                        enabled = !state.isRunning
                    )
                }
            }

            // Map preview
            Text(
                text = "地图预览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val mv = MapView(ctx)
                            mv.setTileSource(TileSourceFactory.MAPNIK)
                            mv.setMultiTouchControls(true)
                            mv.controller.setZoom(15.0)
                            val lat = state.latText.toDoubleOrNull() ?: 39.9042
                            val lng = state.lngText.toDoubleOrNull() ?: 116.4074
                            mv.controller.setCenter(GeoPoint(lat, lng))
                            // Create marker with proper map context
                            val m = Marker(mv)
                            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mv.overlays.add(m)
                            mv.tag = m
                            mv
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { map ->
                            val lat = state.latText.toDoubleOrNull()
                            val lng = state.lngText.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                val point = GeoPoint(lat, lng)
                                map.controller.animateTo(point)
                                val marker = map.tag as? Marker
                                if (marker != null) {
                                    marker.position = point
                                    marker.title = "虚拟位置"
                                    marker.snippet = "%.6f, %.6f".format(lat, lng)
                                }
                                map.invalidate()
                            }
                        }
                    )
                }
            }

            // Start/Stop button
            Button(
                onClick = {
                    val lat = state.latText.toDoubleOrNull()
                    val lng = state.lngText.toDoubleOrNull()

                    if (lat == null || lng == null) {
                        vm.setStatus("请输入有效的经纬度")
                        return@Button
                    }
                    if (lat < -90 || lat > 90) {
                        vm.setStatus("纬度必须在 -90 到 90 之间")
                        return@Button
                    }
                    if (lng < -180 || lng > 180) {
                        vm.setStatus("经度必须在 -180 到 180 之间")
                        return@Button
                    }

                    if (!state.devModeEnabled) {
                        vm.setStatus("请先开启开发者模拟位置（见帮助）")
                        vm.showDeveloperDialog()
                        return@Button
                    }

                    if (!state.hasLocationPermission) {
                        vm.setStatus("需要位置权限")
                        return@Button
                    }

                    if (state.isRunning) {
                        LocationService.stop(context)
                        vm.setRunning(false)
                        vm.setStatus("已停止虚拟定位")
                    } else {
                        LocationService.start(context, lat, lng)
                        vm.setRunning(true)
                        vm.setStatus("虚拟定位已开启: %.6f, %.6f".format(lat, lng))
                        Toast.makeText(
                            context,
                            "虚拟定位已开启",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (state.isRunning) "停止虚拟定位" else "开启虚拟定位",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Footer hint
            Text(
                text = "注意：部分 App 会检测 Mock Location，请知悉。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun VirtuaLocTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = MaterialTheme.colorScheme.primary,
            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
            errorContainer = MaterialTheme.colorScheme.errorContainer
        ),
        content = content
    )
}
