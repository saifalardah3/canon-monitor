package com.example.canonmultimonitor

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.canonmultimonitor.model.CameraConfig
import com.example.canonmultimonitor.network.CanonCameraController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val controllers = mutableStateListOf<CanonCameraController>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during monitoring
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system bars for immersive director monitor mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Default 5 cameras configuration for Mobile Hotspot (192.168.43.x)
        val defaultCameras = listOf(
            CameraConfig(1, "CAM 1 (A-Cam)", "192.168.43.101"),
            CameraConfig(2, "CAM 2 (B-Cam)", "192.168.43.102"),
            CameraConfig(3, "CAM 3 (C-Cam)", "192.168.43.103"),
            CameraConfig(4, "CAM 4 (D-Cam)", "192.168.43.104"),
            CameraConfig(5, "CAM 5 (E-Cam)", "192.168.43.105")
        )

        controllers.addAll(defaultCameras.map { CanonCameraController(it) })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F0F0F)
                ) {
                    MonitorApp(controllers)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllers.forEach { it.stop() }
    }
}

@Composable
fun MonitorApp(controllers: List<CanonCameraController>) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFullscreenController by remember { mutableStateOf<CanonCameraController?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedSettingsController by remember { mutableStateOf<CanonCameraController?>(null) }

    // Start streaming for all cameras
    LaunchedEffect(Unit) {
        controllers.forEach { controller ->
            controller.start(coroutineScope)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedFullscreenController != null) {
            // Fullscreen Single Camera View
            FullscreenMonitorView(
                controller = selectedFullscreenController!!,
                onBack = { selectedFullscreenController = null },
                onTrigger = {
                    coroutineScope.launch {
                        selectedFullscreenController!!.triggerShutter()
                    }
                }
            )
        } else {
            // Multi-view Grid View (1 to 5 cameras)
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Red, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CANON MULTI-MONITOR PRO (5 CAMS)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                controllers.forEach { it.triggerShutter() }
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Trigger All", tint = Color.Red)
                        }
                    }
                }

                // Grid layout (3 columns top, 2 bottom)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(controllers) { controller ->
                        CameraGridTile(
                            controller = controller,
                            onClick = { selectedFullscreenController = controller },
                            onSettingsClick = {
                                selectedSettingsController = controller
                                showSettingsDialog = true
                            }
                        )
                    }
                }
            }
        }

        // IP Settings Dialog
        if (showSettingsDialog && selectedSettingsController != null) {
            var tempIp by remember { mutableStateOf(selectedSettingsController!!.config.ipAddress) }
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text(text = "تعديل عنوان IP الكاميرا") },
                text = {
                    Column {
                        Text(text = selectedSettingsController!!.config.name, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempIp,
                            onValueChange = { tempIp = it },
                            label = { Text("IP Address") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedSettingsController!!.config.ipAddress = tempIp.trim()
                        selectedSettingsController!!.stop()
                        selectedSettingsController!!.start(coroutineScope)
                        showSettingsDialog = false
                    }) {
                        Text("حفظ وإعادة الاتصال")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
fun CameraGridTile(
    controller: CanonCameraController,
    onClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val frame by controller.currentFrame.collectAsState()
    val isConnected by controller.isConnected.collectAsState()
    val fps by controller.fps.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, if (isConnected) Color(0xFF2E7D32) else Color(0xFF424242), RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        if (frame != null && isConnected) {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = controller.config.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Red,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "جاري الاتصال بـ ${controller.config.ipAddress}...",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // Overlay Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isConnected) Color.Green else Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${controller.config.name} (${controller.config.ipAddress})",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isConnected) {
                    Text(
                        text = "$fps FPS",
                        color = Color.Yellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.LightGray,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onSettingsClick() }
                )
            }
        }
    }
}

@Composable
fun FullscreenMonitorView(
    controller: CanonCameraController,
    onBack: () -> Unit,
    onTrigger: () -> Unit
) {
    val frame by controller.currentFrame.collectAsState()
    val isConnected by controller.isConnected.collectAsState()
    val fps by controller.fps.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (frame != null && isConnected) {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = controller.config.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x88000000))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = controller.config.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "[$fps FPS]",
                    color = Color.Yellow,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = onTrigger,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
                Spacer(modifier = Modifier.width(4.dp))
                Text("التقاط / تسجيل")
            }
        }
    }
}
