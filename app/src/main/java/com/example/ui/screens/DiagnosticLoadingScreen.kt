package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DiagnosticLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "neural_boot")
    
    // Animations for effects
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    val scanlineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanline"
    )

    // Terminal Messages logic
    val statusMessages = listOf(
        "> INITIALIZING NEURAL CORE...",
        "> SCANNING OBD-II INTERFACE...",
        "> INTERCEPTING ECU FAULT CODES...",
        "> ANALYZING TELEMETRY STREAMS...",
        "> CHECKING PENDING & STORED DTCs...",
        "> SYNCING WITH CLOUD DIAGNOSTICS...",
        "> SYSTEM STATUS: OPTIMAL"
    )
    var currentMsgIndex by remember { mutableIntStateOf(0) }
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(currentMsgIndex) {
        val target = statusMessages[currentMsgIndex]
        displayedText = ""
        target.forEach { char ->
            displayedText += char
            delay(25)
        }
        delay(1000)
        currentMsgIndex = (currentMsgIndex + 1) % statusMessages.size
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MekanikDarkBg),
        contentAlignment = Alignment.Center
    ) {
        // 1. Background Technical Grid
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
            val gridColor = MekanikNeonGreen
            val step = 40.dp.toPx()
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
            }
            for (y in 0..size.height.toInt() step step.toInt()) {
                drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
            }
        }

        // 2. Scanline Flicker Effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        scanlineOffset to Color.White.copy(alpha = 0.02f),
                        scanlineOffset + 0.01f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Container with Neon Glow
            Box(contentAlignment = Alignment.Center) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .blur(40.dp)
                        .alpha(glowAlpha)
                        .background(MekanikNeonGreen, RoundedCornerShape(30.dp))
                )

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mekanik_brand_logo_original_1780274248851),
                        contentDescription = "Mekanik AI",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Laser Scanning Sweep
                    val sweepPos by infiniteTransition.animateFloat(
                        initialValue = -0.1f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing)),
                        label = "laser"
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.04f)
                            .offset(y = (sweepPos * 120).dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MekanikNeonGreen, Color.Transparent)
                                )
                            )
                            .blur(1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // 3. Segmented Automotive Progress Bar
            Row(
                modifier = Modifier.width(220.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
                    label = "progress"
                )
                repeat(15) { i ->
                    val isActive = progress > (i.toFloat() / 15f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isActive) MekanikNeonGreen else MekanikDarkGreen.copy(alpha = 0.2f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Typing Terminal Text
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = displayedText,
                    color = MekanikNeonGreen,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.alpha(0.8f)
                )
            }
        }

        // Technical Version Tag
        Text(
            text = "SYSTEM READY // NEURAL ENGINES ACTIVE // v1.0.1",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(0.3f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            ),
            color = MekanikNeonGreen
        )
    }
}
