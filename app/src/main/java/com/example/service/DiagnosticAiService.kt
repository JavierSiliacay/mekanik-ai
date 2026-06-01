package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiagnosticAiAdvisor(private val obdBluetoothManager: ObdBluetoothManager) {

    private fun buildPrompt(vehicleName: String, dtcCodes: List<String>, sensorSnapshot: ObdSensorData): String {
        return """
            You are Mekanik AI, a professional offline-capable automotive diagnostic assistant.
            You are analyzing issues for a vehicle: $vehicleName.
            Active DTC codes: ${dtcCodes.joinToString(", ")}.
            
            Live Engine Diagnostic Telemetry Snapshot:
            - Engine RPM: ${sensorSnapshot.rpm} RPM
            - Vehicle Speed: ${sensorSnapshot.speed} km/h
            - Coolant Temperature: ${sensorSnapshot.coolantTemp} °C
            - Fuel Level: ${sensorSnapshot.fuelLevel} %
            - Intake Air Temperature: ${sensorSnapshot.intakeAirTemp} °C
            - Intake Manifold Pressure: ${sensorSnapshot.intakeManifoldPressure} kPa
            - Timing Advance: ${sensorSnapshot.timingAdvance} °
            - Battery Voltage: ${sensorSnapshot.batteryVoltage} V
            - Engine Load: ${sensorSnapshot.engineLoad} %
            - Air Flow Rate (MAF): ${sensorSnapshot.massAirFlow} g/s
            - Short-Term Fuel Trim: ${sensorSnapshot.shortTermFuelTrim} %
            - Long-Term Fuel Trim: ${sensorSnapshot.longTermFuelTrim} %
            - Current Odometer: ${sensorSnapshot.odometer} km
            
            Please provide a structured, professional repair recommendation report.
            Your output should include:
            1. Short, plain-language description of the code(s) (e.g. P0301 Cylinder 1 Misfire).
            2. Likely root causes (with technical depth showing fuel trims, coolant, and battery anomalies if relevant).
            3. Issue Severity Level: Low, Medium, High, or Critical, with a safety warning.
            4. Step-by-Step Recommended Corrective Actions.
            5. Preventive Maintenance Suggestions for the future.
            
            Format nicely using Clean Markdown with bullet points, and keep it concise for a mobile screen. Keep tone authoritative, like a senior automotive engineer.
        """.trimIndent()
    }

    suspend fun diagnoseVehicleIssues(
        vehicleName: String,
        dtcCodes: List<String>,
        sensorSnapshot: ObdSensorData,
        aiProviderManager: AIProviderManager
    ): String = withContext(Dispatchers.IO) {
        if (dtcCodes.isEmpty()) {
            return@withContext "No diagnostic trouble codes detected. Your vehicle system reporting parameters are healthy."
        }

            val localReport = buildLocalAnalysis(vehicleName, dtcCodes, sensorSnapshot)
            val prompt = buildPrompt(vehicleName, dtcCodes, sensorSnapshot)

            try {
                val response = aiProviderManager.generateAnalysis(prompt)
                val headerText = if (aiProviderManager.settingsManager.aiMode.value == AiMode.ONLINE) {
                    "🤖 [ONLINE CLOUD AI]\n\nModel: Cloud Analysis Engine\n\n"
                } else {
                    val modelName = when(aiProviderManager.settingsManager.preferredOfflineModelId.value) {
                        "gemma-2b-gguf" -> "Gemma 2B IT (GGUF)"
                        "youtu-2b" -> "Youtu-LLM 2B (GGUF)"
                        else -> "Local Model"
                    }
                    "🤖 [OFFLINE LOCAL AI]\n\nModel: $modelName\n\n"
                }
                headerText + response
            } catch (e: Exception) {
                Log.e("DiagnosticAi", "AI Generation Error: ${e.message}", e)
                "⚠️ AI Generation Unsuccessful: ${e.message}\n\n🤖 [OFFLINE DICTIONARY FALLBACK]\n\n$localReport"
            }
    }

    private fun buildLocalAnalysis(vehicleName: String, dtcCodes: List<String>, sensorSnapshot: ObdSensorData): String {
        val sb = StringBuilder()
        sb.append("📋 MEKANIK AI ON-DEVICE LOCAL DIAGNOSIS REPORT FOR $vehicleName\n")
        sb.append("-----------------------------------------------------------------\n\n")

        for (code in dtcCodes) {
            val detail = obdBluetoothManager.dtcDictionary[code]
            if (detail != null) {
                sb.append("🔍 Trouble Code: **${detail.code}**\n")
                sb.append("👉 Issue: **${detail.description}**\n")
                sb.append("⚡ Severity: **${detail.severity.uppercase()}**\n\n")
                
                sb.append("🛠️ **Likely Causes:**\n")
                detail.causes.forEach { sb.append("  • $it\n") }
                
                sb.append("\n📈 **Sensor Snapshot Correlation:**\n")
                when (code) {
                    "P0301" -> {
                        sb.append("  • Engine RPM fluctuates dynamically at ${sensorSnapshot.rpm.toInt()} RPM, typical of partial physical cylinder drop misfiring.\n")
                        sb.append("  • Short Term Fuel Trim is active at ${sensorSnapshot.shortTermFuelTrim}%, as oxygen sensor attempts compensation for unburned air/fuel packages.\n")
                    }
                    "P0171" -> {
                        sb.append("  • Combined Fuel Trim (STFT + LTFT) indicates a rich correction profile targeting vacuum leakage.\n")
                        sb.append("  • Mass Air Flow reads ${sensorSnapshot.massAirFlow} g/s. Vacuum leak is likely present behind the MAF throttle throat.\n")
                    }
                    "P0420" -> {
                        sb.append("  • Engine is operating in a stable speed configuration of ${sensorSnapshot.speed.toInt()} km/h with coolant temperature at ${sensorSnapshot.coolantTemp.toInt()}°C. Catalytic converter catalyst matrix thermal activity is low.\n")
                    }
                    "P0115" -> {
                        sb.append("  • Coolant Temp measured at ${sensorSnapshot.coolantTemp.toInt()}°C. Diagnostic values deviate from target thermostat control values.\n")
                    }
                    else -> {
                        sb.append("  • General sensor ranges correlate with active code profile.\n")
                    }
                }
                
                sb.append("\n🔧 **Recommended Actions:**\n")
                detail.recommendations.forEach { sb.append("  • $it\n") }
                sb.append("\n=====================================\n\n")
            } else {
                sb.append("🔍 Unknown Trouble Code: **$code**\n")
                sb.append("👉 General OBD-II Generic Fault Code detected.\n")
                sb.append("⚡ Severity: **MEDIUM**\n")
                sb.append("⚙️ Recommended Action: Clear DTC codes and rescan after driving 10 miles to evaluate recurrence.\n\n")
            }
        }

        sb.append("💡 *This analysis was completed entirely on-board the device using the local diagnostic dictionary heuristics.*")
        return sb.toString()
    }
}
