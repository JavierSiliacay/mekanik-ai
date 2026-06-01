package com.example.ui.screens

import android.widget.Space
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.DiagnosticScan
import com.example.data.DtcRecord
import com.example.data.Vehicle
import com.example.service.ConnectionStatus
import com.example.service.ObdSensorData
import com.example.ui.MekanikViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// 1. REUSABLE CUSTOM GAUGES & WIDGETS
// ==========================================

@Composable
fun AnalogCircularGauge(
    label: String,
    value: Float,
    maxValue: Float,
    unit: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MekanikNeonGreen
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw the circular analog dial
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10.dp.toPx()

            // 1. Track background
            drawArc(
                color = MekanikDarkGreen.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Ticks
            for (angle in 135..405 step 15) {
                val angleRad = Math.toRadians(angle.toDouble())
                val startX = center.x + (radius - 12.dp.toPx()) * cos(angleRad).toFloat()
                val startY = center.y + (radius - 12.dp.toPx()) * sin(angleRad).toFloat()
                val endX = center.x + radius * cos(angleRad).toFloat()
                val endY = center.y + radius * sin(angleRad).toFloat()

                // Highlight sections (e.g. high values turn orange or red)
                val tickColor = if (angle > 330) MekanikErrorRed.copy(alpha = 0.8f)
                               else if (angle > 290) MekanikWarningYellow.copy(alpha = 0.8f)
                               else MekanikNeonGreen.copy(alpha = 0.3f)

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 3. Swept Active Fill
            val sweep = (animatedValue / maxValue).coerceIn(0f, 1f) * 270f
            drawArc(
                color = accentColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Glow Underline
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }

        // Inner textual readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format(Locale.US, "%.0f", animatedValue),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp
                ),
                color = MekanikTextPrimary
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MekanikNeonGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MekanikTextSecondary
            )
        }
    }
}

@Composable
fun TelemetryLinearProgress(
    label: String,
    value: Float,
    max: Float,
    unit: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (value / max).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MekanikSurface),
        border = BorderStroke(1.dp, MekanikDarkGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MekanikTextSecondary
                    )
                }
                Text(
                    text = "${value.toInt()} $unit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MekanikNeonGreen
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MekanikNeonGreen,
                trackColor = MekanikDarkGreen.copy(alpha = 0.2f)
            )
        }
    }
}

// ==========================================
// SCREEN 1: MULTI-VEHICLE MANAGEMENT
// ==========================================

@Composable
fun VehicleScreen(
    viewModel: MekanikViewModel,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Banner Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car",
                        tint = MekanikNeonGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "MEKANIK GARAGE",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MekanikTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manage your fleet and select vehicles to connect OBD-II. Register make, models, and custom properties offline to log errors accurately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MekanikTextSecondary
                )
            }
        }

        // Action Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Registered Vehicles (${vehicles.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MekanikNeonGreen
            )

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                modifier = Modifier.testTag("add_vehicle_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Vehicle", color = Color.Black)
            }
        }

        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MekanikSurface)
                    .border(BorderStroke(1.dp, MekanikDarkGreen.copy(alpha = 0.3f))),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Garage,
                        contentDescription = "Empty",
                        tint = MekanikDimGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Vehicles Registered Yet",
                        color = MekanikTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please click 'Add Vehicle' above to create your first vehicle profile and start diagnosing.",
                        color = MekanikTextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            vehicles.forEach { vehicle ->
                val isSelected = selectedVehicle?.id == vehicle.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { viewModel.selectVehicle(vehicle) }
                        .testTag("vehicle_card_${vehicle.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MekanikDarkGreen.copy(alpha = 0.2f) else MekanikSurface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MekanikNeonGreen else Color.White.copy(alpha = 0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = vehicle.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MekanikTextPrimary
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MekanikNeonGreen),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${vehicle.year} ${vehicle.make} ${vehicle.model} • ${vehicle.engineType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MekanikTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "Plate: ${vehicle.licensePlate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MekanikTextSecondary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = "Odo: ${vehicle.odometer} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MekanikTextSecondary
                                )
                            }
                        }

                        // Right actions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                IconButton(
                                    onClick = onNavigateToDashboard,
                                    modifier = Modifier
                                        .background(MekanikNeonGreen, RoundedCornerShape(100))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Go",
                                        tint = Color.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            IconButton(
                                onClick = { viewModel.deleteVehicle(vehicle) },
                                modifier = Modifier.testTag("delete_vehicle_${vehicle.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MekanikErrorRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        VehicleAddDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, make, model, year, vin, engine, plate, odo ->
                viewModel.addVehicle(name, make, model, year, vin, engine, plate, odo)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun VehicleAddDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            border = BorderStroke(1.dp, MekanikNeonGreen)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "REGISTER NEW VEHICLE",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MekanikNeonGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vehicle Name (e.g., My Chevy)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MekanikNeonGreen,
                        unfocusedBorderColor = MekanikDimGreen,
                        focusedLabelColor = MekanikNeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_vehicle_name_field")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = make,
                        onValueChange = { make = it },
                        label = { Text("Make") },
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                    OutlinedTextField(
                        value = engine,
                        onValueChange = { engine = it },
                        label = { Text("Engine (e.g. 2.0L Turbo)") },
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it },
                    label = { Text("VIN (17 characters)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MekanikNeonGreen,
                        unfocusedBorderColor = MekanikDimGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it },
                        label = { Text("Plate Number") },
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                    OutlinedTextField(
                        value = odo,
                        onValueChange = { odo = it },
                        label = { Text("Odometer (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MekanikNeonGreen,
                            unfocusedBorderColor = MekanikDimGreen
                        )
                    )
                }

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = errorMsg!!, color = MekanikErrorRed, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MekanikTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || make.isBlank() || model.isBlank()) {
                                errorMsg = "Name, Make, and Model are required."
                                return@Button
                            }
                            val yInt = year.toIntOrNull() ?: 2020
                            val oInt = odo.toIntOrNull() ?: 0
                            onSave(name, make, model, yInt, vin, engine, plate, oInt)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                        modifier = Modifier.testTag("save_vehicle_add_btn")
                    ) {
                        Text("Save Profile", color = Color.Black)
                    }
                }
            }
        }
    }
}


// ==========================================
// SCREEN 2: REAL-TIME VEHICLE TELEMETRY
// ==========================================

@Composable
fun PrimaryRpmBentoCard(rpm: Float, maxRpm: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MekanikSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENGINE SPEED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MekanikTextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MekanikNeonGreen.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, MekanikNeonGreen.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MekanikNeonGreen
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%,.0f", rpm),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    ),
                    color = MekanikNeonGreen
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RPM",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Horizontal gradient meter bar
            val progress = (rpm / maxRpm).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF242424))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MekanikNeonGreen, MekanikDimGreen)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun MetricBentoCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.05f)
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MekanikSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MekanikTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MekanikTextSecondary
                )
            }
        }
    }
}

@Composable
fun AiDiagnosticBentoCard(
    scannedCodes: List<com.example.service.BluetoothDtc>,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFault = scannedCodes.firstOrNull()
    val isFaultActive = activeFault != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFaultActive) MekanikWarningYellow.copy(alpha = 0.5f) else MekanikNeonGreen.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = if (isFaultActive) {
                            listOf(Color(0xFF241505), Color(0xFF121212))
                        } else {
                            listOf(Color(0xFF061A0F), Color(0xFF121212))
                        },
                        radius = 400f
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (isFaultActive) MekanikWarningYellow else MekanikNeonGreen, RoundedCornerShape(6.dp))
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "AI DIAGNOSTIC ASSISTANT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = if (isFaultActive) MekanikWarningYellow else MekanikNeonGreen
                    )
                }

                if (isFaultActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Status: Warning (${activeFault!!.code})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (activeFault.severity.uppercase()) {
                                    "CRITICAL" -> MekanikErrorRed.copy(alpha = 0.2f)
                                    else -> MekanikWarningYellow.copy(alpha = 0.2f)
                                }
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = activeFault.severity.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                ),
                                color = if (activeFault.severity.uppercase() == "CRITICAL") MekanikErrorRed else MekanikWarningYellow
                            )
                        }
                    }

                    Text(
                        text = activeFault.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MekanikTextSecondary,
                        lineHeight = 16.sp
                    )

                    if (activeFault.causes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recommendation: " + (activeFault.causes.firstOrNull() ?: ""),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Status: Clean / Nominal",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MekanikNeonGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "NOMINAL",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                ),
                                color = MekanikNeonGreen
                            )
                        }
                    }
                    Text(
                        text = "All powertrain systems and ECU modules reporting nominal telemetry. There are no active trouble flags isolated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MekanikTextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNavigateToScanner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFaultActive) MekanikWarningYellow else MekanikNeonGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFaultActive) "FULL REPAIR GUIDE" else "INITIATE DIAGNOSTIC SCAN",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: MekanikViewModel,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val liveData by viewModel.liveSensorData.collectAsState()
    val scannedCodes by viewModel.scannedCodes.collectAsState()

    var showConnectorDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (selectedVehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MekanikSurface)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car Select",
                        tint = MekanikDimGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Active Vehicle Selected",
                        style = MaterialTheme.typography.titleLarge,
                        color = MekanikTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please navigate to the Garage tab and select an active vehicle to query live telemetry indicators.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MekanikTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        // Connection Ribbon Status Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> MekanikNeonGreen.copy(alpha = 0.3f)
                    ConnectionStatus.CONNECTING -> MekanikWarningYellow.copy(alpha = 0.3f)
                    else -> Color.White.copy(alpha = 0.05f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(100))
                            .background(
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> MekanikNeonGreen
                                    ConnectionStatus.CONNECTING -> MekanikWarningYellow
                                    ConnectionStatus.ERROR -> MekanikErrorRed
                                    ConnectionStatus.DISCONNECTED -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ELM327 ADAPTER STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MekanikTextSecondary
                        )
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Ready: Connected to $connectedDeviceName"
                                ConnectionStatus.CONNECTING -> "Handshake protocol initiating..."
                                ConnectionStatus.ERROR -> "Fault: Connection error"
                                ConnectionStatus.DISCONNECTED -> "Disconnected: Bluetooth inactive"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MekanikTextPrimary
                        )
                    }
                }

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    TextButton(
                        onClick = { viewModel.disconnectAdapter() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MekanikErrorRed)
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = { showConnectorDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("connect_obd_btn")
                    ) {
                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (connectionStatus != ConnectionStatus.CONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)))
                    .background(MekanikSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth Prompt",
                        tint = MekanikNeonGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "OBD-II Bluetooth Adapter Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MekanikTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Attach your ELM327 Bluetooth key into your car's OBD port, click 'Connect', then select our simulated virtual adapter or any real visible hardware in range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MekanikTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Live dials grid
            Text(
                text = "Live Cluster Bento Telemetry",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MekanikNeonGreen,
                modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
            )

            // 1. Primary Engine speed RPM Bento card (full width)
            PrimaryRpmBentoCard(rpm = liveData.rpm, maxRpm = 8000f)

            // 2. Secondary Metrics (Bento grid of 6 modules: Speed, Coolant, Load, Battery, Throttle, MAF)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBentoCard(
                    label = "Speedometer",
                    value = String.format(Locale.US, "%.0f", liveData.speed),
                    unit = "km/h",
                    modifier = Modifier.weight(1f)
                )
                MetricBentoCard(
                    label = "Coolant Temp",
                    value = String.format(Locale.US, "%.0f", liveData.coolantTemp),
                    unit = "°C",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBentoCard(
                    label = "Engine Load",
                    value = String.format(Locale.US, "%.1f", liveData.engineLoad),
                    unit = "%",
                    modifier = Modifier.weight(1f)
                )
                MetricBentoCard(
                    label = "Battery Voltage",
                    value = String.format(Locale.US, "%.1f", liveData.batteryVoltage),
                    unit = "V",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBentoCard(
                    label = "Throttle Pos",
                    value = String.format(Locale.US, "%.0f", liveData.throttlePosition),
                    unit = "%",
                    modifier = Modifier.weight(1f)
                )
                MetricBentoCard(
                    label = "Mass Air Flow",
                    value = String.format(Locale.US, "%.2f", liveData.massAirFlow),
                    unit = "g/s",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. AI Diagnostic Insight Card
            AiDiagnosticBentoCard(scannedCodes = scannedCodes, onNavigateToScanner = onNavigateToScanner)

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Real-time Oscilloscope Flow Chart scaled in Bento card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REAL-TIME OSCILLOSCOPE FLOW (RPM wave)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MekanikTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated realtime wave plotting based on current RPM
                    val wavePoints = remember { mutableStateListOf<Float>() }
                    LaunchedEffect(liveData.rpm) {
                        wavePoints.add(liveData.rpm)
                        if (wavePoints.size > 20) {
                            wavePoints.removeAt(0)
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        // Background Grid
                        val gridSpace = size.width / 5
                        for (i in 1..4) {
                            drawLine(
                                color = MekanikDarkGreen.copy(alpha = 0.2f),
                                start = Offset(i * gridSpace, 0f),
                                end = Offset(i * gridSpace, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val horizSpace = size.height / 4
                        for (i in 1..3) {
                            drawLine(
                                color = MekanikDarkGreen.copy(alpha = 0.2f),
                                start = Offset(0f, i * horizSpace),
                                end = Offset(size.width, i * horizSpace),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Plot RPM Line
                        if (wavePoints.size > 1) {
                            val step = size.width / 19f
                            var lastX = 0f
                            var lastY = size.height

                            wavePoints.forEachIndexed { index, rpmVal ->
                                val pct = (rpmVal / 8000f).coerceIn(0f, 1f)
                                val x = index * step
                                val y = size.height - (pct * size.height)

                                drawLine(
                                    color = MekanikNeonGreen,
                                    start = Offset(lastX, lastY),
                                    end = Offset(x, y),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                lastX = x
                                lastY = y
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConnectorDialog) {
        OBDConnectorDialog(
            viewModel = viewModel,
            onDismiss = { showConnectorDialog = false }
        )
    }
}

@Composable
fun OBDConnectorDialog(
    viewModel: MekanikViewModel,
    onDismiss: () -> Unit
) {
    val devices = remember { viewModel.getAvailableScanAdapters() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            border = BorderStroke(1.dp, MekanikNeonGreen)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT OBD-II BLUETOOTH ADAPTER",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MekanikNeonGreen
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Confirm paired ELM327 key devices or pick our built-in simulator engine for a detailed demonstration session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MekanikTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MekanikDarkGreen)

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(devices) { device ->
                        ListItem(
                            headlineContent = { Text(device.first, color = MekanikTextPrimary) },
                            supportingContent = { Text(device.second, color = MekanikTextSecondary, fontFamily = FontFamily.Monospace) },
                            leadingContent = {
                                Icon(
                                    imageVector = if (device.first.contains("Sim")) Icons.Default.SettingsSuggest else Icons.Default.Bluetooth,
                                    contentDescription = "Device",
                                    tint = MekanikNeonGreen
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable {
                                    viewModel.connectToAdapter(device.first, device.second)
                                    onDismiss()
                                }
                                .testTag("device_item_${device.first.replace(" ", "_")}")
                        )
                        HorizontalDivider(color = MekanikDarkGreen.copy(alpha = 0.3f))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MekanikTextSecondary)
                    }
                }
            }
        }
    }
}


// ==========================================
// SCREEN 3: ECU DIAGNOSTIC SCAN & AI TROUBLESHOOTING
// ==========================================

@Composable
fun ScannerScreen(
    viewModel: MekanikViewModel,
    modifier: Modifier = Modifier
) {
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedCodes by viewModel.scannedCodes.collectAsState()
    val aiLoading by viewModel.isAiLoading.collectAsState()
    val aiReport by viewModel.aiAnalysisReport.collectAsState()

    var useCloudAnalysis by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (selectedVehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MekanikSurface)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Locked",
                        tint = MekanikDimGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanner Active Only with Selected Vehicle",
                        style = MaterialTheme.typography.titleMedium,
                        color = MekanikTextPrimary
                    )
                }
            }
            return@Column
        }

        // Active vehicle banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVE SCANNED TARGET",
                        style = MaterialTheme.typography.labelSmall,
                        color = MekanikTextSecondary
                    )
                    Text(
                        text = "${selectedVehicle!!.year} ${selectedVehicle!!.make} ${selectedVehicle!!.model}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MekanikNeonGreen
                    )
                }

                Card(colors = CardDefaults.cardColors(containerColor = MekanikDarkGreen)) {
                    Text(
                        text = "ECU CONNECTED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MekanikNeonGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Actions Control center
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DTC DIAGNOSTIC CENTRE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MekanikNeonGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect Bluetooth to read error codes dynamically from the engine. Toggle internet parameters to use on-board localized rules (100% offline) or Cloud Deep Analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MekanikTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (useCloudAnalysis) "Deep Scan: AI Advisor (Cloud Enabled)" else "On-Device AI (100% Offline)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MekanikTextPrimary
                        )
                        Text(
                            text = if (useCloudAnalysis) "Assembles prompt incorporating sensor values." else "Resolves parameters locally in real-time.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MekanikTextSecondary
                        )
                    }
                    Switch(
                        checked = useCloudAnalysis,
                        onCheckedChange = { useCloudAnalysis = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MekanikNeonGreen,
                            uncheckedThumbColor = MekanikTextSecondary,
                            uncheckedTrackColor = MekanikDarkGreen
                        ),
                        modifier = Modifier.testTag("ai_mode_switch")
                    )
                }

                HorizontalDivider(color = MekanikDarkGreen.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 16.dp))

                // Scan Action Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.scanVehicleTroubleCodes(useCloudAnalysis) },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                        enabled = connectionStatus == ConnectionStatus.CONNECTED && !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trigger_dtc_scan_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.BugReport, contentDescription = "Scan", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DTC SCAN", color = Color.Black)
                        }
                    }

                    Button(
                        onClick = { viewModel.clearTroubleCodesOnEcu() },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikErrorRed),
                        enabled = connectionStatus == ConnectionStatus.CONNECTED && !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_dtc_btn")
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Clear", tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CLEAR DTC", color = Color.Black)
                    }
                }

                if (connectionStatus != ConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ OBD connection requested for scan operations. Select connection on Dashboard tab first.",
                        color = MekanikWarningYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // On-device AI localized settings
        OnDeviceAiSettingsCard(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // Intercept fault injector (Demo injector triggers codes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
            border = BorderStroke(1.dp, MekanikDarkGreen.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "EMULATE SYSTEM FAULT INJECTOR (Demo Mode)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MekanikTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val faultsList = listOf(
                        "P0301" to "Cylinder 1 Misfire",
                        "P0171" to "System Too Lean",
                        "P0420" to "Cat Catalyst Weakness",
                        "P0115" to "Coolant Sensor Fail",
                        "P0500" to "VSS Malfunction"
                    )

                    faultsList.forEach { pair ->
                        Card(
                            modifier = Modifier
                                .clickable {
                                    viewModel.triggerSimulatedEngineFault(pair.first)
                                }
                                .testTag("inject_${pair.first}"),
                            colors = CardDefaults.cardColors(containerColor = MekanikSurfaceVariant),
                            border = BorderStroke(1.dp, MekanikDimGreen)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(pair.first, color = MekanikNeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(pair.second, color = MekanikTextPrimary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scan Results
        Text(
            text = "DTC Readouts Intercepted",
            style = MaterialTheme.typography.titleMedium,
            color = MekanikNeonGreen,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (scannedCodes.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MekanikSurface)
                    .border(BorderStroke(1.dp, MekanikDarkGreen.copy(alpha = 0.3f)))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "All good",
                        tint = MekanikNeonGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Trouble Codes Intercepted", color = MekanikTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Run a DTC scan to pull diagnostic metrics from the car ECU.", color = MekanikTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            scannedCodes.forEach { code ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = when (code.severity.uppercase()) {
                            "CRITICAL" -> MekanikErrorRed
                            "HIGH" -> MekanikErrorRed.copy(alpha = 0.6f)
                            "MEDIUM" -> MekanikWarningYellow
                            else -> MekanikNeonGreen
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = code.code,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MekanikNeonGreen
                            )

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (code.severity.uppercase()) {
                                        "CRITICAL" -> MekanikErrorRed
                                        "HIGH" -> MekanikErrorRed.copy(alpha = 0.2f)
                                        "MEDIUM" -> MekanikWarningYellow.copy(alpha = 0.2f)
                                        else -> MekanikNeonGreen.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Text(
                                    text = code.severity.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (code.severity.uppercase() == "CRITICAL") Color.Black else MekanikTextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = code.description, style = MaterialTheme.typography.bodyLarge, color = MekanikTextPrimary)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Likely Causes:", fontSize = 12.sp, color = MekanikTextSecondary, fontWeight = FontWeight.Bold)
                        code.causes.forEach { cause ->
                            Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(100)).background(MekanikNeonGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = cause, fontSize = 12.sp, color = MekanikTextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Render AI Report
        if (aiLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                border = BorderStroke(1.dp, MekanikNeonGreen)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MekanikNeonGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MEKANIK AI ADVISOR CRUNCHING DATA...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MekanikNeonGreen
                    )
                    Text(
                        text = "Analyzing ECU telemetry and computing corrective measures entirely offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MekanikTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (!aiReport.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mekanik AI Diagnostic Analysis",
                style = MaterialTheme.typography.titleMedium,
                color = MekanikNeonGreen,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().testTag("ai_report_card"),
                colors = CardDefaults.cardColors(containerColor = MekanikSurfaceVariant),
                border = BorderStroke(1.dp, MekanikNeonGreen)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, "AI", tint = MekanikNeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MEKANIK ADVISORY SUMMARY",
                            fontWeight = FontWeight.Bold,
                            color = MekanikTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = aiReport!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        color = MekanikTextPrimary
                    )
                }
            }
        }
    }
}


// ==========================================
// SCREEN 4: DIAGNOSTIC HISTORY TIMELINE Log
// ==========================================

@Composable
fun HistoryScreen(
    viewModel: MekanikViewModel,
    modifier: Modifier = Modifier
) {
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val scans by viewModel.selectedVehicleScans.collectAsState()

    var activeReportDetail by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (selectedVehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MekanikSurface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select active vehicle in Garage to view historical scans.",
                    color = MekanikTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DIAGNOSTIC HISTORY LOGS",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MekanikNeonGreen
                )
                Text(
                    text = "${selectedVehicle!!.year} ${selectedVehicle!!.make} ${selectedVehicle!!.model}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MekanikTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MekanikSurface)
                    .border(BorderStroke(1.dp, MekanikDarkGreen.copy(alpha = 0.3f))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.History, "No history", tint = MekanikDimGreen, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Diagnostic Sessions Logged", color = MekanikTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Launch scans from the Scanner tab to cache diagnostics and repair timelines here to review offline.", color = MekanikTextSecondary, textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
        } else {
            scans.forEach { scan ->
                val dateStr = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(scan.timestamp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("scan_item_${scan.id}"),
                    colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                    border = BorderStroke(1.dp, if (scan.totalDtcCount > 0) MekanikWarningYellow.copy(alpha = 0.6f) else MekanikDarkGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dateStr,
                                    color = MekanikTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Odometer Reading: ${scan.odometerReading} km",
                                    fontSize = 12.sp,
                                    color = MekanikTextSecondary
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteHistoricalScan(scan) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Log",
                                    tint = MekanikErrorRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MekanikDarkGreen.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = scan.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (scan.totalDtcCount > 0) MekanikWarningYellow else MekanikTextPrimary
                        )

                        if (scan.totalDtcCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    // Set detail reports review dialog
                                    activeReportDetail = scan.overview
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MekanikDarkGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = "Reports", tint = MekanikNeonGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review Advisory Report", color = MekanikNeonGreen)
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeReportDetail != null) {
        Dialog(onDismissRequest = { activeReportDetail = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                border = BorderStroke(1.dp, MekanikNeonGreen)
            ) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = "HISTORICAL SCAN ADVISORY",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MekanikNeonGreen
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = activeReportDetail!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MekanikTextPrimary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { activeReportDetail = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen)
                        ) {
                            Text("Close", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnDeviceAiSettingsCard(
    viewModel: MekanikViewModel,
    modifier: Modifier = Modifier
) {
    val modelPath by viewModel.localModelPath.collectAsState()
    val isInitialized by viewModel.isLocalAiInitialized.collectAsState()
    val initError by viewModel.localAiInitError.collectAsState()
    val isLoading by viewModel.localAiLoading.collectAsState()

    var tempPath by remember(modelPath) { mutableStateOf(modelPath) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MekanikSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(if (isInitialized) MekanikNeonGreen else Color.Gray, RoundedCornerShape(6.dp))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Local AI",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "ON-DEVICE AI (GENERIC)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isInitialized) MekanikNeonGreen else Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "On-device GenAI allows running optimized LLMs (Gemma, Llama, Phi) directly on your mobile device hardware entirely offline. Follow official guides to transfer model binaries (.bin) to your device standard directories.",
                style = MaterialTheme.typography.bodySmall,
                color = MekanikTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Text field for model path setting
            OutlinedTextField(
                value = tempPath,
                onValueChange = { tempPath = it },
                label = { Text("Local Model Path (.bin or .task)") },
                placeholder = { Text("/data/local/tmp/llm/model.bin") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MekanikNeonGreen,
                    unfocusedBorderColor = MekanikDimGreen,
                    focusedLabelColor = MekanikNeonGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("on_device_path_input")
            )

            if (initError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: $initError",
                    color = MekanikErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.saveAndInitializeLocalAi(tempPath) },
                    colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                    enabled = !isLoading && tempPath.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("load_local_model_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(if (isInitialized) "RELOAD MODEL" else "INITIALIZE MODEL", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                if (isInitialized) {
                    Button(
                        onClick = { viewModel.unloadLocalAi() },
                        colors = ButtonDefaults.buttonColors(containerColor = MekanikErrorRed.copy(alpha = 0.8f)),
                        modifier = Modifier.weight(1f).testTag("unload_local_model_btn")
                    ) {
                        Text("UNLOAD", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instructions details info
            Card(
                colors = CardDefaults.cardColors(containerColor = MekanikSurfaceVariant),
                border = BorderStroke(1.dp, MekanikDimGreen.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Steps",
                            tint = MekanikNeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "How to load a model (.bin):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MekanikNeonGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Download optimized or compatible on-device model binaries.\n" +
                               "2. Push the file to your physical phone or emulator storage via command:\n" +
                               "   adb push command:\n" +
                               "   adb push model.bin /data/local/tmp/model.bin\n" +
                               "3. Enter the path above and tap 'INITIALIZE MODEL' to bind On-Device AI.",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MekanikTextSecondary,
                            lineHeight = 14.sp
                        )
                    )
                }
            }
        }
    }
}
