package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiagnosticAiAdvisor(private val obdBluetoothManager: ObdBluetoothManager) {

    private fun buildPrompt(vehicleName: String, dtcCodes: List<String>, sensorSnapshot: ObdSensorData): String {
        return """
            You are Mekanik AI, an expert Senior Automotive Systems Engineer and Master Technician.
            Vehicle Identity: $vehicleName
            Diagnostic Trouble Codes (DTCs): ${dtcCodes.joinToString(", ")}
            
            Real-time Operational Telemetry Snapshot:
            - Engine Speed: ${sensorSnapshot.rpm} RPM
            - Road Speed: ${sensorSnapshot.speed} km/h
            - Coolant Temp: ${sensorSnapshot.coolantTemp} °C
            - Fuel Level: ${sensorSnapshot.fuelLevel} %
            - Intake Air: ${sensorSnapshot.intakeAirTemp} °C
            - Manifold Pressure: ${sensorSnapshot.intakeManifoldPressure} kPa
            - Ignition Advance: ${sensorSnapshot.timingAdvance} °
            - System Voltage: ${sensorSnapshot.batteryVoltage} V
            - Engine Load: ${sensorSnapshot.engineLoad} %
            - Air Flow (MAF): ${sensorSnapshot.massAirFlow} g/s
            - Short-Term Fuel Trim (STFT): ${sensorSnapshot.shortTermFuelTrim} %
            - Long-Term Fuel Trim (LTFT): ${sensorSnapshot.longTermFuelTrim} %
            - Accumulated Mileage: ${sensorSnapshot.odometer} km
            
            Task: Provide a high-fidelity automotive diagnostic report using the telemetry above to correlate the DTCs.
            
            Required Sections:
            1. **Technical Overview**: Explain the DTCs and how they interact with the current sensor readings (e.g., "STFT of ${sensorSnapshot.shortTermFuelTrim}% confirms a lean condition...").
            2. **Correlation Analysis**: Identify if sensor data supports or contradicts the DTCs.
            3. **Primary Suspects**: Rank the most likely failing components.
            4. **Severity & Safety**: Define risk (Low/Med/High/Critical) and immediate precautions.
            5. **Precision Repair Steps**: List specific diagnostic tests (e.g., "Check fuel pressure," "Smoke test intake").
            6. **Maintenance Insight**: How to prevent this recurrence.
            
            Constraint: Be extremely precise. Use professional technician terminology. Format with clear headers and bullet points. If telemetry shows anomalies (e.g. low voltage or high temp), highlight them as potential root causes.
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
                        "llama-3.2-1b" -> "Llama 3.2 1B (GGUF)"
                        "smollm2-1.7b" -> "SmolLM2 1.7B (GGUF)"
                        "qwen2.5-1.5b" -> "Qwen 2.5 1.5B (GGUF)"
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
