package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import com.example.service.AiMode
import com.example.service.DownloadState
import com.example.ui.screens.AiSettingsDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.MekanikRepository
import com.example.service.ConnectionStatus
import com.example.ui.AutomotiveChatViewModel
import com.example.ui.MekanikViewModel
import com.example.ui.components.AutomotiveChatWidget
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.VehicleScreen
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Handle permission denial - maybe show a snackbar or a dialog
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        // Custom exit animation for the splash screen
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val slideUp = android.animation.ObjectAnimator.ofFloat(
                splashScreenView.view,
                android.view.View.TRANSLATION_Y,
                0f,
                -splashScreenView.view.height.toFloat()
            )
            slideUp.interpolator = AnticipateInterpolator()
            slideUp.duration = 600L

            // Call remove at the end of the animation.
            slideUp.doOnEnd { splashScreenView.remove() }

            // Run animation.
            slideUp.start()
        }

        enableEdgeToEdge()

        // 1. Initialize local Room SQLite Database and Repo mapping
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MekanikRepository(
            vehicleDao = database.vehicleDao(),
            diagnosticScanDao = database.diagnosticScanDao(),
            dtcRecordDao = database.dtcRecordDao()
        )

        // 2. Create the unified controller factory
        val viewModelFactory = MekanikViewModel.Companion.Factory(application, repository)

        // Jetpack Compose MVVM View Model
        val viewModel: MekanikViewModel = ViewModelProvider(this, viewModelFactory)[MekanikViewModel::class.java]

        // Keep the splash screen on-screen until the ViewModel is initialized
        splashScreen.setKeepOnScreenCondition {
            !viewModel.isInitialized.value
        }

        setContent {
            MekanikAITheme {
                // Use the already initialized viewModel
                val chatViewModel: AutomotiveChatViewModel = viewModel(
                    factory = AutomotiveChatViewModel.Companion.Factory(
                        application,
                        viewModel.aiProviderManager
                    )
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    MekanikAppShell(viewModel)
                    
                    // Floating AI Widget on top of everything
                    AutomotiveChatWidget(chatViewModel)
                }
            }
        }
    }
}

enum class MekanikTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GARAGE("Garage", Icons.Default.DirectionsCar),
    DASHBOARD("Dashboard", Icons.Default.Speed),
    SCANNER("Scanner", Icons.Default.BugReport),
    HISTORY("History", Icons.Default.History)
}

@Composable
fun ScanAiIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(31.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw the camera/scanner viewfinder brackets around the AI Spark
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val length = 6.dp.toPx()
            val color = Color.Black

            // Top-Left corner
            drawLine(color, start = Offset(2.dp.toPx(), 2.dp.toPx()), end = Offset(length, 2.dp.toPx()), strokeWidth = strokeWidth)
            drawLine(color, start = Offset(2.dp.toPx(), 2.dp.toPx()), end = Offset(2.dp.toPx(), length), strokeWidth = strokeWidth)

            // Top-Right corner
            drawLine(color, start = Offset(size.width - 2.dp.toPx(), 2.dp.toPx()), end = Offset(size.width - length, 2.dp.toPx()), strokeWidth = strokeWidth)
            drawLine(color, start = Offset(size.width - 2.dp.toPx(), 2.dp.toPx()), end = Offset(size.width - 2.dp.toPx(), length), strokeWidth = strokeWidth)

            // Bottom-Left corner
            drawLine(color, start = Offset(2.dp.toPx(), size.height - 2.dp.toPx()), end = Offset(length, size.height - 2.dp.toPx()), strokeWidth = strokeWidth)
            drawLine(color, start = Offset(2.dp.toPx(), size.height - 2.dp.toPx()), end = Offset(2.dp.toPx(), size.height - length), strokeWidth = strokeWidth)

            // Bottom-Right corner
            drawLine(color, start = Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), end = Offset(size.width - length, size.height - 2.dp.toPx()), strokeWidth = strokeWidth)
            drawLine(color, start = Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), end = Offset(size.width - 2.dp.toPx(), size.height - length), strokeWidth = strokeWidth)
        }

        // Inner Spark icon
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun MekanikAppShell(viewModel: MekanikViewModel) {
    var activeTab by remember { mutableStateOf(MekanikTab.GARAGE) }
    var showConnectionPromptDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Reactively monitor connection attributes to notify visually
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val batteryAlert by viewModel.batteryAlert.collectAsState()
    
    // Monitors for offline model lists and network warning triggers
    val aiNetworkWarning by viewModel.aiNetworkWarning.collectAsState()
    val offlineModels by viewModel.offlineModels.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MekanikDarkBg
    ) {
        if (isTablet) {
            // TABLET / FOLDABLE Layout: Persistent Ergonomic Side Navigation Rail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MekanikSurface,
                    contentColor = MekanikNeonGreen,
                    header = {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "MK",
                                color = MekanikNeonGreen,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    },
                    modifier = Modifier.testTag("tablet_nav_rail")
                ) {
                    MekanikTab.entries.forEach { tab ->
                        val isTabSelected = activeTab == tab
                        NavigationRailItem(
                            selected = isTabSelected,
                            onClick = { activeTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = MekanikNeonGreen,
                                indicatorColor = MekanikNeonGreen,
                                unselectedIconColor = MekanikTextSecondary,
                                unselectedTextColor = MekanikTextSecondary
                            ),
                            modifier = Modifier.testTag("rail_item_${tab.name.lowercase()}")
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MekanikDarkGreen
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MekanikDarkBg)
                ) {
                    Scaffold(
                        topBar = {
                            MekanikTopHeaderBanner(
                                selectedVehicle = selectedVehicle,
                                connStatus = connStatus,
                                batteryAlert = batteryAlert,
                                onSettingsClick = { showSettingsDialog = true }
                            )
                        },
                        containerColor = MekanikDarkBg,
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        MekanikScreenContent(
                            activeTab = activeTab,
                            viewModel = viewModel,
                            onNavigateToDashboard = { activeTab = MekanikTab.DASHBOARD },
                            onNavigateToScanner = { activeTab = MekanikTab.SCANNER },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        } else {
            // MOBILE Layout: Bottom Navigation Bar with safe window drawing insets
            Scaffold(
                topBar = {
                    MekanikTopHeaderBanner(
                        selectedVehicle = selectedVehicle,
                        connStatus = connStatus,
                        batteryAlert = batteryAlert,
                        onSettingsClick = { showSettingsDialog = true }
                    )
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        NavigationBar(
                            containerColor = MekanikSurface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .height(80.dp)
                                .testTag("mobile_bottom_nav")
                        ) {
                            // 1. Garage
                            NavigationBarItem(
                                selected = activeTab == MekanikTab.GARAGE,
                                onClick = { activeTab = MekanikTab.GARAGE },
                                icon = {
                                    Icon(
                                        imageVector = MekanikTab.GARAGE.icon,
                                        contentDescription = MekanikTab.GARAGE.label
                                    )
                                },
                                label = { Text(MekanikTab.GARAGE.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = MekanikNeonGreen,
                                    indicatorColor = MekanikNeonGreen,
                                    unselectedIconColor = MekanikTextSecondary,
                                    unselectedTextColor = MekanikTextSecondary
                                ),
                                modifier = Modifier.weight(1f).testTag("nav_item_garage")
                            )

                            // 2. Dashboard
                            NavigationBarItem(
                                selected = activeTab == MekanikTab.DASHBOARD,
                                onClick = { activeTab = MekanikTab.DASHBOARD },
                                icon = {
                                    Icon(
                                        imageVector = MekanikTab.DASHBOARD.icon,
                                        contentDescription = MekanikTab.DASHBOARD.label
                                    )
                                },
                                label = { Text(MekanikTab.DASHBOARD.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = MekanikNeonGreen,
                                    indicatorColor = MekanikNeonGreen,
                                    unselectedIconColor = MekanikTextSecondary,
                                    unselectedTextColor = MekanikTextSecondary
                                ),
                                modifier = Modifier.weight(1f).testTag("nav_item_dashboard")
                            )

                            // 3. Spacer for center button
                            Spacer(modifier = Modifier.weight(1.2f))

                            // 4. Scanner
                            NavigationBarItem(
                                selected = activeTab == MekanikTab.SCANNER,
                                onClick = { activeTab = MekanikTab.SCANNER },
                                icon = {
                                    Icon(
                                        imageVector = MekanikTab.SCANNER.icon,
                                        contentDescription = MekanikTab.SCANNER.label
                                    )
                                },
                                label = { Text(MekanikTab.SCANNER.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = MekanikNeonGreen,
                                    indicatorColor = MekanikNeonGreen,
                                    unselectedIconColor = MekanikTextSecondary,
                                    unselectedTextColor = MekanikTextSecondary
                                ),
                                modifier = Modifier.weight(1f).testTag("nav_item_scanner")
                            )

                            // 5. History
                            NavigationBarItem(
                                selected = activeTab == MekanikTab.HISTORY,
                                onClick = { activeTab = MekanikTab.HISTORY },
                                icon = {
                                    Icon(
                                        imageVector = MekanikTab.HISTORY.icon,
                                        contentDescription = MekanikTab.HISTORY.label
                                    )
                                },
                                label = { Text(MekanikTab.HISTORY.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = MekanikNeonGreen,
                                    indicatorColor = MekanikNeonGreen,
                                    unselectedIconColor = MekanikTextSecondary,
                                    unselectedTextColor = MekanikTextSecondary
                                ),
                                modifier = Modifier.weight(1f).testTag("nav_item_history")
                            )
                        }

                        // Overlapping Center circular GCash-style Action Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .offset(y = (-26).dp)
                                .zIndex(10f)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    if (connStatus == ConnectionStatus.CONNECTED) {
                                        activeTab = MekanikTab.SCANNER
                                        viewModel.scanVehicleTroubleCodes(true)
                                    } else {
                                        showConnectionPromptDialog = true
                                    }
                                },
                                containerColor = MekanikNeonGreen,
                                contentColor = Color.Black,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("gcash_scan_ai_btn"),
                                elevation = FloatingActionButtonDefaults.elevation(8.dp)
                            ) {
                                ScanAiIcon()
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Scan AI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MekanikNeonGreen
                            )
                        }
                    }
                },
                containerColor = MekanikDarkBg,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                MekanikScreenContent(
                    activeTab = activeTab,
                    viewModel = viewModel,
                    onNavigateToDashboard = { activeTab = MekanikTab.DASHBOARD },
                    onNavigateToScanner = { activeTab = MekanikTab.SCANNER },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        if (showConnectionPromptDialog) {
            AlertDialog(
                onDismissRequest = { showConnectionPromptDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "Scan with AI", tint = MekanikNeonGreen)
                        Text("DTC AI Scan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "You must select a vehicle and connect with ELM327 Bluetooth on the Dashboard first before initiating a diagnostic scan.",
                        color = MekanikTextSecondary
                    )
                },
                containerColor = MekanikSurface,
                titleContentColor = Color.White,
                textContentColor = MekanikTextSecondary,
                confirmButton = {
                    Button(
                        onClick = {
                            showConnectionPromptDialog = false
                            activeTab = MekanikTab.DASHBOARD
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen)
                    ) {
                        Text("Go to Dashboard", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConnectionPromptDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MekanikTextSecondary)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSettingsDialog) {
            AiSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (aiNetworkWarning != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissNetworkWarning() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline Connection Warning",
                            tint = MekanikErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Internet Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(
                        text = aiNetworkWarning ?: "Active AI Provider requires internet, but device is offline.",
                        color = MekanikTextSecondary
                    )
                },
                containerColor = MekanikSurface,
                titleContentColor = Color.White,
                textContentColor = MekanikTextSecondary,
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissNetworkWarning()
                            viewModel.scanVehicleTroubleCodes(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen)
                    ) {
                        Text("Retry Connection", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        val hasInstalledLocal = offlineModels.any { it.downloadState == DownloadState.INSTALLED }
                        if (hasInstalledLocal) {
                            Button(
                                onClick = {
                                    viewModel.setAiMode(AiMode.OFFLINE)
                                    viewModel.dismissNetworkWarning()
                                    viewModel.scanVehicleTroubleCodes(false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekanikDarkGreen),
                                border = BorderStroke(1.dp, MekanikNeonGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("warning_switch_offline_btn")
                            ) {
                                Text("Switch to Installed Offline Model", color = MekanikNeonGreen, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Note: No offline model downloaded yet. To scan offline, download a model in AI Settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MekanikTextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.dismissNetworkWarning()
                                    showSettingsDialog = true
                                },
                                modifier = Modifier.testTag("warning_open_settings_btn")
                            ) {
                                Text("Open AI Settings", color = MekanikNeonGreen, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = { viewModel.dismissNetworkWarning() }
                            ) {
                                Text("Cancel", color = MekanikTextSecondary)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MekanikTopHeaderBanner(
    selectedVehicle: com.example.data.Vehicle?,
    connStatus: ConnectionStatus,
    batteryAlert: String?,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MekanikSurface)
            .statusBarsPadding()
            .drawBehind {
                // Emerald accent border underline
                drawLine(
                    color = MekanikDarkGreen,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Space to balance the icon on the right and keep title perfectly centered
            Spacer(modifier = Modifier.size(48.dp))

            // Center Column (Title and status)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MEKANIK AI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = MekanikNeonGreen
                )
                if (selectedVehicle != null) {
                    Text(
                        text = "Vehicle: ${selectedVehicle.name} (${if (connStatus == ConnectionStatus.CONNECTED) "OBD Connected" else "OBD Offline"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connStatus == ConnectionStatus.CONNECTED) MekanikNeonGreen else MekanikTextSecondary
                    )
                } else {
                    Text(
                        text = "Offboard: Select Garage Profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MekanikTextSecondary
                    )
                }
            }

            // Pinned Settings Gear (Far Right)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("app_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "AI Configuration settings",
                    tint = MekanikNeonGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Global Battery Alert Strip
        AnimatedVisibility(
            visible = batteryAlert != null && connStatus == ConnectionStatus.CONNECTED,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (batteryAlert?.startsWith("CRITICAL") == true)
                            MekanikErrorRed.copy(alpha = 0.9f) else MekanikWarningYellow.copy(alpha = 0.9f)
                    )
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = "Battery Alert",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = batteryAlert ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MekanikScreenContent(
    activeTab: MekanikTab,
    viewModel: MekanikViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (activeTab) {
            MekanikTab.GARAGE -> VehicleScreen(viewModel, onNavigateToDashboard)
            MekanikTab.DASHBOARD -> DashboardScreen(
                viewModel,
                onNavigateToScanner = onNavigateToScanner
            )
            MekanikTab.SCANNER -> ScannerScreen(viewModel)
            MekanikTab.HISTORY -> HistoryScreen(viewModel)
        }
    }
}
