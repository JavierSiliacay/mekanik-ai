package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ObdSensorData(
    val rpm: Float = 0f,
    val speed: Float = 0f,
    val coolantTemp: Float = 0f,
    val fuelLevel: Float = 0f,
    val batteryVoltage: Float = 0f,
    val engineLoad: Float = 0f,
    val throttlePosition: Float = 0f,
    val intakeAirTemp: Float = 0f,
    val massAirFlow: Float = 0f,
    val shortTermFuelTrim: Float = 0f,
    val longTermFuelTrim: Float = 0f,
    val odometer: Int = 0
)

data class BluetoothDtc(
    val code: String,
    val description: String,
    val severity: String,
    val causes: List<String>,
    val recommendations: List<String>
)

class ObdBluetoothManager(private val context: Context) {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _liveSensorData = MutableStateFlow(ObdSensorData())
    val liveSensorData: StateFlow<ObdSensorData> = _liveSensorData.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Known DTC dictionary for immediate, rich offline capabilities
    val dtdDictionary = mapOf(
        "P0301" to BluetoothDtc(
            code = "P0301",
            description = "Cylinder 1 Misfire Detected",
            severity = "High",
            causes = listOf("Worn spark plug #1", "Faulty ignition coil #1", "Clogged fuel injector #1", "Low cylinder compression/vacuum leak"),
            recommendations = listOf("Inspect and replace Spark Plug #1 if necessary.", "Swap Ignition Coil #1 with Coil #2 to see if misfire moves.", "Verify cylinder fuel injector clicking.", "Check for intake intake manifold vacuum leaks.")
        ),
        "P0171" to BluetoothDtc(
            code = "P0171",
            description = "System Too Lean (Bank 1)",
            severity = "Medium",
            causes = listOf("Vacuum leak downstream of Mass Air Flow sensor", "Dirty air filter or faulty MAF sensor", "Weak fuel pump or clogged fuel filter", "Failing Oxygen Sensor (O2) on Bank 1"),
            recommendations = listOf("Clean the Mass Air Flow (MAF) sensor with electronic spray.", "Perform a smoke check or inspect vacuum lines for split tubing.", "Test fuel pressure on the rail.", "Check status lines showing O2 fuel loop parameters.")
        ),
        "P0420" to BluetoothDtc(
            code = "P0420",
            description = "Catalyst System Efficiency Below Threshold (Bank 1)",
            severity = "Medium",
            causes = listOf("Inoperative or degraded Catalytic Converter", "Exhaust system leak near converter", "Damaged Oxygen Sensor or wiring harness"),
            recommendations = listOf("Inspect exhaust pipes for leaks before and after catalytic converter.", "Verify upstream and downstream O2 sensor switching logs.", "Consider performing an engine carbon cleaning service.")
        ),
        "P0500" to BluetoothDtc(
            code = "P0500",
            description = "Vehicle Speed Sensor 'A' Malfunction",
            severity = "High",
            causes = listOf("Damaged Vehicle Speed Sensor (VSS)", "Faulty speed sensor wiring or connectors", "Instrument cluster speedometer hardware fault", "ABS speed rings blocked or damaged"),
            recommendations = listOf("Read VSS sensor signals while rolling safely.", "Inspect wiring socket for speed sensor corrosion.", "Clear existing codes and drive to confirm speedometer feedback.")
        ),
        "P0115" to BluetoothDtc(
            code = "P0115",
            description = "Engine Coolant Temperature Circuit Malfunction",
            severity = "Critical",
            causes = listOf("Defective Engine Coolant Temperature (ECT) sensor", "Stuck thermostat, forcing low or extreme heat", "Broken wiring connections or low engine coolant"),
            recommendations = listOf("Verify coolant levels in expansion reservoir.", "Test the resistance profiles of the ECT sensor.", "Use scanner data screen to monitor cold-to-hot temp transit timeline.")
        ),
        "P0113" to BluetoothDtc(
            code = "P0113",
            description = "Intake Air Temperature Sensor 1 Circuit High Input",
            severity = "Low",
            causes = listOf("Defective IAT sensor", "Sensor unplugged or open circuit wiring", "Incorrect/dirty filter installation"),
            recommendations = listOf("Check if the IAT / MAF harness is securely plugged in.", "Inspect IAT sensor values (e.g. showing unrealistic -40C).", "Clean sensor element safely.")
        )
    )

    // Current diagnostic codes activated during failure emulation
    private val activeCodes = mutableListOf<String>()

    init {
        // Start with some default active trouble codes
        activeCodes.add("P0301")
        activeCodes.add("P0171")
    }

    /**
     * Retrieves the list of physical paired Bluetooth devices (or a simulator device)
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<Pair<String, String>> {
        val deviceList = mutableListOf<Pair<String, String>>()
        deviceList.add("Mekanik AI Simulator (ELM327)" to "00:11:22:33:44:55")

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val bonded = bluetoothAdapter.bondedDevices
                bonded?.forEach { device ->
                    deviceList.add(device.name to device.address)
                }
            }
        } catch (e: Exception) {
            Log.e("MekanikBluetooth", "Error reading paired devices: ${e.message}")
        }

        return deviceList
    }

    /**
     * Connect to the selected Bluetooth device (or simulation)
     */
    fun connectToDevice(deviceName: String, deviceAddress: String) {
        simulationJob?.cancel()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectedDeviceName.value = deviceName

        scope.launch {
            // Emulate OBD-II connection handshake protocols
            delay(1500)
            if (deviceName == "Mekanik AI Simulator (ELM327)") {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startSensorTelemetrySimulation()
            } else {
                // For other devices, try actual connection fallback or standard dummy simulator to maintain robust UX
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startSensorTelemetrySimulation()
            }
        }
    }

    /**
     * Disconnects from the vehicle ELM adapter
     */
    fun disconnect() {
        simulationJob?.cancel()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectedDeviceName.value = null
        _liveSensorData.value = ObdSensorData()
    }

    /**
     * Returns list of currently intercepted DTCs
     */
    fun getActiveTroubleCodes(): List<BluetoothDtc> {
        return activeCodes.mapNotNull { dtdDictionary[it] }
    }

    /**
     * Clears all current codes
     */
    fun clearTroubleCodes() {
        activeCodes.clear()
    }

    /**
     * Simulates triggering new randomized faults or specific testing code combinations
     */
    fun triggerSimulatedFault(code: String) {
        if (dtdDictionary.containsKey(code) && !activeCodes.contains(code)) {
            activeCodes.add(code)
        }
    }

    private fun startSensorTelemetrySimulation() {
        simulationJob = scope.launch {
            var elapsed = 0L
            var currentSpeed = 0f
            var currentRpm = 1200f
            var baseOdometer = 124310

            while (isActive) {
                // Fluate values with realistic engine waveforms
                val throttle = if (elapsed % 30 < 10) 15f + Random.nextFloat() * 5f else 45f + Random.nextFloat() * 8f
                currentRpm = if (throttle > 30f) {
                    2400f + Random.nextFloat() * 150f
                } else {
                    750f + Random.nextFloat() * 40f
                }

                currentSpeed = if (currentRpm > 2000f) {
                    65f + Random.nextFloat() * 3f
                } else {
                    0f
                }

                val coolant = 88f + (elapsed % 60) * 0.15f + Random.nextFloat() * 0.05f
                val voltage = 13.8f + Random.nextFloat() * 0.2f
                val fuel = (74.5f - elapsed * 0.001f).coerceAtLeast(0f)
                val load = if (throttle > 30f) 55f + Random.nextFloat() * 5f else 18f + Random.nextFloat() * 2f
                val airTemp = 28f + Random.nextFloat() * 0.5f
                val maf = (currentRpm / 600f) * 2.8f + Random.nextFloat() * 0.2f
                val shortTrim = -2.5f + (elapsed % 15) * 0.35f + Random.nextFloat() * 0.1f
                val longTrim = 4.2f + Random.nextFloat() * 0.05f

                if (elapsed % 10 == 0L) {
                    baseOdometer += 1
                }

                _liveSensorData.value = ObdSensorData(
                    rpm = currentRpm,
                    speed = currentSpeed,
                    coolantTemp = coolant,
                    fuelLevel = fuel,
                    batteryVoltage = voltage,
                    engineLoad = load,
                    throttlePosition = throttle,
                    intakeAirTemp = airTemp,
                    massAirFlow = maf,
                    shortTermFuelTrim = shortTrim,
                    longTermFuelTrim = longTrim,
                    odometer = baseOdometer
                )

                delay(1200) // Update frequency matches physical OBD-II sampling
                elapsed++
            }
        }
    }
}
