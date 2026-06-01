package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.AiMode
import com.example.service.DownloadState
import com.example.service.OfflineModel
import com.example.ui.MekanikViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    viewModel: MekanikViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiMode by viewModel.aiMode.collectAsState()
    val preferredOnlineModel by viewModel.preferredOnlineModel.collectAsState()
    val preferredOfflineModelId by viewModel.preferredOfflineModelId.collectAsState()
    
    val isInternetAvailable by viewModel.isInternetAvailable.collectAsState()
    val offlineModels by viewModel.offlineModels.collectAsState()
    
    val onlineConnectionStatus by viewModel.onlineConnectionStatus.collectAsState()
    val apiHealthStatus by viewModel.apiHealthStatus.collectAsState()
    val responseLatency by viewModel.responseLatency.collectAsState()

    val hasAnyInstalledLocal = remember(offlineModels) {
        offlineModels.any { it.downloadState == DownloadState.INSTALLED }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MekanikDarkBg,
            border = BorderStroke(1.dp, MekanikNeonGreen.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Topic Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI settings",
                            tint = MekanikNeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AI CONFIGURATION",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_settings_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = MekanikTextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = MekanikDarkGreen,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: AI MODE SWITCHING
                    item {
                        Text(
                            text = "AI REASONING MODE",
                            color = MekanikNeonGreen,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Online Card Option
                            Card(
                                onClick = { viewModel.setAiMode(AiMode.ONLINE) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (aiMode == AiMode.ONLINE) MekanikNeonGreen else Color.White.copy(alpha = 0.05f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (aiMode == AiMode.ONLINE) MekanikDarkGreen else MekanikSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_mode_online_select_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = "Online AI",
                                        tint = if (aiMode == AiMode.ONLINE) MekanikNeonGreen else MekanikTextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Online Cloud AI",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (aiMode == AiMode.ONLINE) MekanikNeonGreen else Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "High Capability",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekanikTextSecondary
                                    )
                                }
                            }

                            // Offline Card Option
                            val offlineEnabled = hasAnyInstalledLocal
                            Card(
                                onClick = {
                                    if (offlineEnabled) {
                                        viewModel.setAiMode(AiMode.OFFLINE)
                                    } else {
                                        Toast.makeText(context, "Please download an offline model first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (aiMode == AiMode.OFFLINE) {
                                        MekanikNeonGreen
                                    } else {
                                        Color.White.copy(alpha = 0.05f)
                                    }
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        aiMode == AiMode.OFFLINE -> MekanikDarkGreen
                                        !offlineEnabled -> MekanikSurface.copy(alpha = 0.4f)
                                        else -> MekanikSurface
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_mode_offline_select_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Offline AI",
                                        tint = when {
                                            aiMode == AiMode.OFFLINE -> MekanikNeonGreen
                                            !offlineEnabled -> MekanikTextSecondary.copy(alpha = 0.4f)
                                            else -> MekanikTextSecondary
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "On-Device AI",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            aiMode == AiMode.OFFLINE -> MekanikNeonGreen
                                            !offlineEnabled -> Color.White.copy(alpha = 0.4f)
                                            else -> Color.White
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (offlineEnabled) "100% Offline" else "Unavailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (offlineEnabled) MekanikTextSecondary else MekanikErrorRed.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Disabled offline mode explanation block
                        if (!hasAnyInstalledLocal) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MekanikErrorRed.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, MekanikErrorRed.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "No Local Models",
                                        tint = MekanikErrorRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "On-Device mode is currently disabled. To enable private local processing, you must download a model from the manager below.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekanikErrorRed
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MekanikNeonGreen, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE: ${aiMode.name}",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 2: ONLINE PROVIDER CONFIG
                    item {
                        Text(
                            text = "ONLINE CLOUD PROVIDER CONFIG",
                            color = MekanikNeonGreen,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MekanikSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CLOUD MODEL:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MekanikTextSecondary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(MekanikSurfaceVariant, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = preferredOnlineModel,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "CONNECTION STATUS:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekanikTextSecondary
                                    )
                                    Text(
                                        text = if (isInternetAvailable) "Internet Connected" else "No Connection",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isInternetAvailable) MekanikNeonGreen else MekanikErrorRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "API HEALTH:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekanikTextSecondary
                                    )
                                    Text(
                                        text = if (isInternetAvailable) apiHealthStatus else "Unavailable (Offline)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isInternetAvailable && apiHealthStatus == "Healthy") MekanikNeonGreen else MekanikErrorRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "LAST LATENCY:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MekanikTextSecondary
                                    )
                                    Text(
                                        text = responseLatency,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MekanikNeonGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 3: OFFLINE MODEL MANAGER
                    item {
                        Text(
                            text = "OFFLINE MODEL MANAGEMENT",
                            color = MekanikNeonGreen,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    items(offlineModels) { model ->
                        val isPreferred = preferredOfflineModelId == model.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPreferred) MekanikSurfaceVariant else MekanikSurface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isPreferred) MekanikNeonGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.04f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Title / Badge Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Size: ${model.sizeLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MekanikTextSecondary
                                        )
                                    }

                                    // Local state badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = when (model.downloadState) {
                                                    DownloadState.INSTALLED -> MekanikNeonGreen.copy(alpha = 0.15f)
                                                    DownloadState.DOWNLOADING -> MekanikWarningYellow.copy(alpha = 0.15f)
                                                    DownloadState.PAUSED -> Color.Gray.copy(alpha = 0.15f)
                                                    DownloadState.VERIFYING -> MekanikNeonGreen.copy(alpha = 0.3f)
                                                    DownloadState.NOT_INSTALLED -> Color.White.copy(alpha = 0.05f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (model.downloadState) {
                                                DownloadState.INSTALLED -> "INSTALLED"
                                                DownloadState.DOWNLOADING -> "DOWNLOADING"
                                                DownloadState.PAUSED -> "PAUSED"
                                                DownloadState.VERIFYING -> "VERIFYING"
                                                DownloadState.NOT_INSTALLED -> "NOT DOWNLOADED"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = when (model.downloadState) {
                                                DownloadState.INSTALLED -> MekanikNeonGreen
                                                DownloadState.DOWNLOADING -> MekanikWarningYellow
                                                DownloadState.PAUSED -> Color.White
                                                DownloadState.VERIFYING -> MekanikNeonGreen
                                                DownloadState.NOT_INSTALLED -> MekanikTextSecondary
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MekanikTextSecondary
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Download Track Progress
                                if (model.downloadState == DownloadState.DOWNLOADING || model.downloadState == DownloadState.PAUSED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { model.progress },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp),
                                            color = MekanikNeonGreen,
                                            trackColor = MekanikSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "${(model.progress * 100).toInt()}%",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }

                                    if (model.speedLabel.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Download Speed: ${model.speedLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MekanikTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Interactive actions based on states
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (model.downloadState) {
                                        DownloadState.NOT_INSTALLED -> {
                                            Button(
                                                onClick = { viewModel.startModelDownload(model.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("download_model_btn_${model.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Install model",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("DOWNLOAD", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        DownloadState.DOWNLOADING -> {
                                            Button(
                                                onClick = { viewModel.pauseModelDownload(model.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MekanikWarningYellow),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("pause_download_btn_${model.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Pause,
                                                    contentDescription = "Pause",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("PAUSE", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        DownloadState.PAUSED -> {
                                            Button(
                                                onClick = { viewModel.startModelDownload(model.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MekanikNeonGreen),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("resume_download_btn_${model.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Resume",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("RESUME", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = { viewModel.deleteOfflineModel(model.id) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MekanikErrorRed),
                                                border = BorderStroke(1.dp, MekanikErrorRed.copy(alpha = 0.5f)),
                                                modifier = Modifier.testTag("delete_paused_btn_${model.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Cancel",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        DownloadState.VERIFYING -> {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MekanikNeonGreen,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = model.speedLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MekanikNeonGreen,
                                                    modifier = Modifier.padding(start = 28.dp)
                                                )
                                            }
                                        }

                                        DownloadState.INSTALLED -> {
                                            if (isPreferred) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            MekanikNeonGreen.copy(alpha = 0.08f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            MekanikNeonGreen.copy(alpha = 0.3f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Active offline model",
                                                            tint = MekanikNeonGreen,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "ACTIVE DEFAULT MODEL",
                                                            color = MekanikNeonGreen,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.setPreferredOfflineModelId(model.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MekanikDarkGreen),
                                                    border = BorderStroke(1.dp, MekanikNeonGreen.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("set_default_offline_model_${model.id}")
                                                ) {
                                                    Text(
                                                        text = "SELECT AS DEFAULT",
                                                        color = MekanikNeonGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Integrity check button
                                            TextButton(
                                                onClick = {
                                                    viewModel.verifyModelIntegrity(model.id) { passed, message ->
                                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MekanikNeonGreen),
                                                modifier = Modifier.testTag("verify_model_btn_${model.id}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Verified,
                                                        contentDescription = "Verify",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text("VERIFY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Delete model file button
                                            IconButton(
                                                onClick = { viewModel.deleteOfflineModel(model.id) },
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = MekanikErrorRed),
                                                modifier = Modifier.testTag("delete_installed_model_btn_${model.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Model"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4: DEEP COMPARISON SUMMARY MATRIX
                    item {
                        Text(
                            text = "AI CLOUD VS ON-DEVICE REASONING PROFILE",
                            color = MekanikNeonGreen,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MekanikSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MekanikDarkGreen)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "☁️ ONLINE CLOUD MODE",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MekanikNeonGreen
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Uses high-capacity cloud models for deep analysis.\n" +
                                                   "• Requires continuous internet.\n" +
                                                   "• Accesses latest releases without downloads.\n" +
                                                   "• Tiny memory footprints locally.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MekanikTextSecondary,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📱 ON-DEVICE OFFLINE MODE",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MekanikNeonGreen
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Fully server-independent local reasoning.\n" +
                                                   "• Works deep in remote garages, zero data leaks.\n" +
                                                   "• Instant responses with zero transfer speeds.\n" +
                                                   "• Consumes device storage and run battery.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MekanikTextSecondary,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
