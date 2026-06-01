package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
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
    val intakeManifoldPressure: Float = 0f,
    val timingAdvance: Float = 0f,
    val shortTermFuelTrim: Float = 0f,
    val longTermFuelTrim: Float = 0f,
    val odometer: Float = 0f,
    val vin: String = ""
)

data class BluetoothDtc(
    val code: String,
    val description: String,
    val severity: String,
    val causes: List<String>,
    val recommendations: List<String>
)

data class PidConfig(
    val pid: String,
    val bytes: Int,
    val description: String,
    val unit: String = "",
    val fact: Float = 1f,
    val div: Float = 1f,
    val offs: Float = 0f
)

class ObdBluetoothManager(private val context: Context) {

    private val TAG = "MekanikObd"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Extensive PID library from Circuito AI context
    private val PIDS = mapOf(
        "04" to PidConfig("04", 1, "Engine Load", "%", 100f, 255f),
        "05" to PidConfig("05", 1, "Coolant Temp", "°C", 1f, 1f, -40f),
        "06" to PidConfig("06", 1, "Short Term Fuel Trim", "%", 100f, 128f, -100f),
        "07" to PidConfig("07", 1, "Long Term Fuel Trim", "%", 100f, 128f, -100f),
        "0B" to PidConfig("0B", 1, "Intake Map", "kPa"),
        "0C" to PidConfig("0C", 2, "RPM", "RPM", 1f, 4f),
        "0D" to PidConfig("0D", 1, "Speed", "km/h"),
        "0E" to PidConfig("0E", 1, "Timing Advance", "°", 1f, 2f, -64f),
        "0F" to PidConfig("0F", 1, "Intake Air Temp", "°C", 1f, 1f, -40f),
        "10" to PidConfig("10", 2, "MAF", "g/s", 1f, 100f),
        "11" to PidConfig("11", 1, "Throttle", "%", 100f, 255f),
        "2F" to PidConfig("2F", 1, "Fuel Level", "%", 100f, 255f),
        "42" to PidConfig("42", 2, "Voltage", "V", 1f, 1000f),
        "A6" to PidConfig("A6", 4, "Odometer", "km", 1f, 10f)
    )

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _liveSensorData = MutableStateFlow(ObdSensorData())
    val liveSensorData: StateFlow<ObdSensorData> = _liveSensorData.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    // Known DTC dictionary for immediate, rich offline capabilities
    val dtcDictionary = mapOf(
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

    // Current diagnostic codes
    private val activeCodes = mutableListOf<String>()

    init {
        // Production: Start with empty diagnostic state
    }

    /**
     * Retrieves the list of physical paired Bluetooth devices (or a simulator device)
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<Pair<String, String>> {
        val deviceList = mutableListOf<Pair<String, String>>()
        
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
     * Connect to the selected Bluetooth device
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceName: String, deviceAddress: String) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectedDeviceName.value = deviceName

        scope.launch {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                // Initialize ELM327
                if (initializeElm327()) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    startDataPolling()
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                disconnect()
            }
        }
    }

    private suspend fun initializeElm327(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Reset ELM327
                sendCommand("ATZ")
                delay(1000)
                // Echo off
                sendCommand("ATE0")
                // Linefeeds off
                sendCommand("ATL0")
                // Select protocol (Auto)
                sendCommand("ATSP0")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}")
                false
            }
        }
    }

    private fun startDataPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    // Optimized sequential polling for critical metrics based on Circuito AI logic
                    val rpm = queryPid("010C")
                    val speed = queryPid("010D")
                    val load = queryPid("0104")
                    val temp = queryPid("0105")
                    val voltage = queryPid("0142")
                    val fuel = queryPid("012F")
                    val maf = queryPid("0110")
                    val map = queryPid("010B")
                    val timing = queryPid("010E")
                    val throttle = queryPid("0111")
                    val iat = queryPid("010F")
                    val stft = queryPid("0106")
                    val ltft = queryPid("0107")

                    _liveSensorData.value = _liveSensorData.value.copy(
                        rpm = rpm ?: _liveSensorData.value.rpm,
                        speed = speed ?: _liveSensorData.value.speed,
                        engineLoad = load ?: _liveSensorData.value.engineLoad,
                        coolantTemp = temp ?: _liveSensorData.value.coolantTemp,
                        batteryVoltage = voltage ?: _liveSensorData.value.batteryVoltage,
                        fuelLevel = fuel ?: _liveSensorData.value.fuelLevel,
                        massAirFlow = maf ?: _liveSensorData.value.massAirFlow,
                        intakeManifoldPressure = map ?: _liveSensorData.value.intakeManifoldPressure,
                        timingAdvance = timing ?: _liveSensorData.value.timingAdvance,
                        throttlePosition = throttle ?: _liveSensorData.value.throttlePosition,
                        intakeAirTemp = iat ?: _liveSensorData.value.intakeAirTemp,
                        shortTermFuelTrim = stft ?: _liveSensorData.value.shortTermFuelTrim,
                        longTermFuelTrim = ltft ?: _liveSensorData.value.longTermFuelTrim
                    )

                    // Periodically try to fetch VIN if missing
                    if (_liveSensorData.value.vin.isEmpty()) {
                        val vin = queryVin()
                        if (vin != null) {
                            _liveSensorData.value = _liveSensorData.value.copy(vin = vin)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(200) // Fast polling for responsive UI
            }
        }
    }

    private suspend fun queryPid(pidCode: String): Float? {
        val pid = pidCode.substring(2) // e.g. "0C"
        val config = PIDS[pid] ?: return null

        return withContext(Dispatchers.IO) {
            val response = sendCommand(pidCode)
            // Look for 41 + PID (Service 01 response)
            val successPrefix = "41$pid"
            if (response.contains(successPrefix)) {
                try {
                    val rawData = response.substringAfter(successPrefix).trim().replace(" ", "")
                    if (rawData.length < config.bytes * 2) return@withContext null
                    
                    val hexData = rawData.take(config.bytes * 2)
                    var rawValue = 0L
                    for (i in 0 until config.bytes) {
                        val byteHex = hexData.substring(i * 2, i * 2 + 2)
                        rawValue = (rawValue shl 8) or byteHex.toLong(16)
                    }

                    (rawValue.toFloat() * config.fact / config.div) + config.offs
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun queryVin(): String? {
        return withContext(Dispatchers.IO) {
            val response = sendCommand("0902")
            if (response.contains("4902")) {
                val raw = response.substringAfter("4902").replace(" ", "").trim()
                val bytes = mutableListOf<Int>()
                for (i in 0 until raw.length - 1 step 2) {
                    try {
                        val b = raw.substring(i, i + 2).toInt(16)
                        if (b in 32..126) bytes.add(b)
                    } catch (e: Exception) {}
                }
                val vin = bytes.map { it.toChar() }.joinToString("").trim()
                if (vin.length >= 11) vin else null
            } else null
        }
    }

    private suspend fun sendCommand(cmd: String): String {
        return withContext(Dispatchers.IO) {
            val out = outputStream ?: throw IOException("Output stream is null")
            val input = inputStream ?: throw IOException("Input stream is null")

            out.write((cmd + "\r").toByteArray())
            out.flush()

            val buffer = StringBuilder()
            var char: Int
            // ELM327 responses end with '>'
            while (true) {
                char = input.read()
                if (char == -1 || char.toChar() == '>') break
                buffer.append(char.toChar())
            }
            val result = buffer.toString().trim()
            Log.d(TAG, "> $cmd | < $result")
            result
        }
    }

    /**
     * Disconnects from the vehicle ELM adapter
     */
    fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }

        inputStream = null
        outputStream = null
        bluetoothSocket = null

        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectedDeviceName.value = null
        _liveSensorData.value = ObdSensorData()
    }

    /**
     * Returns list of currently intercepted DTCs
     */
    suspend fun getActiveTroubleCodes(): List<BluetoothDtc> {
        return withContext(Dispatchers.IO) {
            if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val response = sendCommand("03") // Mode 03: Request trouble codes
                    // response is usually like "43 01 33 00 00 00 00" or similar.
                    // Simplified parsing for ELM327:
                    val codes = parseDtcResponse(response)
                    activeCodes.clear()
                    activeCodes.addAll(codes)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch DTCs: ${e.message}")
                }
            }
            activeCodes.mapNotNull { dtcDictionary[it] }
        }
    }

    private fun parseDtcResponse(response: String): List<String> {
        val cleaned = response.replace(" ", "").replace("\r", "").replace("\n", "")
        // Mode 03 response starts with 43. Each DTC is 2 bytes (4 hex chars).
        if (cleaned.contains("43")) {
            val data = cleaned.substringAfter("43")
            val codes = mutableListOf<String>()
            for (i in 0 until data.length - 3 step 4) {
                val hex = data.substring(i, i + 4)
                if (hex == "0000") continue
                
                // Advanced decoding from Circuito AI bitwise logic
                try {
                    val b1 = hex.substring(0, 2).toInt(16)
                    val b2 = hex.substring(2, 4)
                    
                    val prefixes = arrayOf("P", "C", "B", "U")
                    val prefix = prefixes[(b1 and 0xC0) shr 6]
                    val d1 = (b1 and 0x30) shr 4
                    val d2 = b1 and 0x0F
                    
                    codes.add("$prefix$d1$d2$b2")
                } catch (e: Exception) {
                    Log.e(TAG, "DTC Decode error: ${e.message}")
                }
            }
            return codes
        }
        return emptyList()
    }

    /**
     * Clears all current codes
     */
    fun clearTroubleCodes() {
        scope.launch {
            if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                sendCommand("04") // OBD-II Clear DTCs command
                activeCodes.clear()
            }
        }
    }
}
