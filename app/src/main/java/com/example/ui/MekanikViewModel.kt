package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MekanikViewModel(
    application: Application,
    private val repository: MekanikRepository
) : AndroidViewModel(application) {

    // --- Complete AI Configuration & Model Management dependencies ---
    // Moved to top to ensure initialization before they are referenced by other properties or the init block
    val settingsManager = SettingsManager(application)
    val networkMonitor = NetworkMonitor(application)
    val downloadManager = ModelDownloadManager(application)
    val aiProviderManager = AIProviderManager(application, settingsManager, networkMonitor)

    // Bluetooth manager & AI
    val obdManager = ObdBluetoothManager(application)
    private val aiAdvisor = DiagnosticAiAdvisor(obdManager)

    // State flows
    val vehicles: StateFlow<List<Vehicle>> = repository.allVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    // Observable flows from OBD adapter
    val connectionStatus: StateFlow<ConnectionStatus> = obdManager.connectionStatus
    val connectedDeviceName: StateFlow<String?> = obdManager.connectedDeviceName
    val liveSensorData: StateFlow<ObdSensorData> = obdManager.liveSensorData

    // DTC Scanner State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedCodes = MutableStateFlow<List<BluetoothDtc>>(emptyList())
    val scannedCodes: StateFlow<List<BluetoothDtc>> = _scannedCodes.asStateFlow()

    // AI diagnostic report status
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiAnalysisReport = MutableStateFlow<String?>(null)
    val aiAnalysisReport: StateFlow<String?> = _aiAnalysisReport.asStateFlow()

    // Historical timeline state for the selected vehicle
    private val _selectedVehicleScans = MutableStateFlow<List<DiagnosticScan>>(emptyList())
    val selectedVehicleScans: StateFlow<List<DiagnosticScan>> = _selectedVehicleScans.asStateFlow()

    private val _selectedVehicleDtcHistory = MutableStateFlow<List<DtcRecord>>(emptyList())
    val selectedVehicleDtcHistory: StateFlow<List<DtcRecord>> = _selectedVehicleDtcHistory.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // Monitor selected vehicle to update its specific historic logs
        viewModelScope.launch {
            selectedVehicle.collect { vehicle ->
                if (vehicle != null) {
                    refreshVehicleHistory(vehicle.id)
                } else {
                    _selectedVehicleScans.value = emptyList()
                    _selectedVehicleDtcHistory.value = emptyList()
                }
            }
        }

        // Simulate or perform required initialization
        viewModelScope.launch {
            // Fetch remote config (API keys, etc.) to avoid expired token issues
            CloudAiClient.refreshConfig()

            // Add any actual heavy loading here if needed (e.g., pre-loading some data)
            kotlinx.coroutines.delay(1000) // Give splash some time to breathe
            _isInitialized.value = true
        }
    }

    // Expose all preferences & live monitoring states to Compose views
    val aiMode: StateFlow<AiMode> = settingsManager.aiMode
    val preferredOnlineModel: StateFlow<String> = settingsManager.preferredOnlineModel
    val preferredOfflineModelId: StateFlow<String?> = settingsManager.preferredOfflineModelId
    
    val isInternetAvailable: StateFlow<Boolean> = networkMonitor.isInternetAvailable
    val offlineModels: StateFlow<List<OfflineModel>> = downloadManager.models
    
    val onlineConnectionStatus: StateFlow<String> = aiProviderManager.onlineConnectionStatus
    val apiHealthStatus: StateFlow<String> = aiProviderManager.apiHealthStatus
    val responseLatency: StateFlow<String> = aiProviderManager.responseLatency

    private val _aiNetworkWarning = MutableStateFlow<String?>(null)
    val aiNetworkWarning: StateFlow<String?> = _aiNetworkWarning.asStateFlow()

    fun dismissNetworkWarning() {
        _aiNetworkWarning.value = null
    }

    fun setAiMode(mode: AiMode) {
        settingsManager.setAiMode(mode)
    }

    fun setPreferredOnlineModel(model: String) {
        settingsManager.setPreferredOnlineModel(model)
    }

    fun setPreferredOfflineModelId(id: String?) {
        settingsManager.setPreferredOfflineModelId(id)
        viewModelScope.launch(Dispatchers.IO) {
            val model = downloadManager.models.value.find { it.id == id }
            if (model != null && model.downloadState == DownloadState.INSTALLED) {
                val file = java.io.File(getApplication<Application>().filesDir, model.fileName)
                if (file.exists()) {
                    LlamaService.initialize(file.absolutePath)
                }
            } else {
                LlamaService.close()
            }
        }
    }

    fun startModelDownload(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    fun pauseModelDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun deleteOfflineModel(modelId: String) {
        downloadManager.deleteModel(modelId)
        if (preferredOfflineModelId.value == modelId) {
            LlamaService.close()
        }
    }

    fun verifyModelIntegrity(modelId: String, onResult: (Boolean, String) -> Unit) {
        downloadManager.verifyModelIntegrity(modelId, onResult)
    }


    fun selectVehicle(vehicle: Vehicle?) {
        _selectedVehicle.value = vehicle
        _aiAnalysisReport.value = null
        _scannedCodes.value = emptyList()
    }

    private fun refreshVehicleHistory(vehicleId: Int) {
        viewModelScope.launch {
            repository.getScansForVehicle(vehicleId).collect {
                _selectedVehicleScans.value = it
            }
        }
        viewModelScope.launch {
            repository.getHistoryForVehicle(vehicleId).collect {
                _selectedVehicleDtcHistory.value = it
            }
        }
    }

    // Vehicle Management Actions
    fun addVehicle(
        name: String,
        make: String,
        model: String,
        year: Int,
        vin: String,
        engineType: String,
        licensePlate: String,
        odometer: Int
    ) {
        viewModelScope.launch {
            val vehicle = Vehicle(
                name = name,
                make = make,
                model = model,
                year = year,
                vin = vin,
                engineType = engineType,
                licensePlate = licensePlate,
                odometer = odometer
            )
            val newId = repository.insertVehicle(vehicle)
            if (_selectedVehicle.value == null) {
                _selectedVehicle.value = vehicle.copy(id = newId.toInt())
            }
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            if (_selectedVehicle.value?.id == vehicle.id) {
                _selectedVehicle.value = null
            }
            repository.deleteVehicle(vehicle)
        }
    }

    // OBD Connections
    fun getAvailableScanAdapters(): List<Pair<String, String>> {
        return obdManager.getPairedBluetoothDevices()
    }

    fun connectToAdapter(name: String, address: String) {
        obdManager.connectToDevice(name, address)
    }

    fun disconnectAdapter() {
        obdManager.disconnect()
        _scannedCodes.value = emptyList()
        _aiAnalysisReport.value = null
    }

    // Trigger Fault Code Scanning
    fun scanVehicleTroubleCodes(useLocalAiAnalysis: Boolean) {
        val vehicle = _selectedVehicle.value ?: return
        if (connectionStatus.value != ConnectionStatus.CONNECTED) return

        // Network connectivity check for Online mode
        if (settingsManager.aiMode.value == AiMode.ONLINE && !networkMonitor.isInternetAvailable.value) {
            _aiNetworkWarning.value = "Your selected online AI mode requires internet connectivity, but the device is currently offline."
            return
        }

        _isScanning.value = true
        _scannedCodes.value = emptyList()
        _aiAnalysisReport.value = null

        viewModelScope.launch {
            // Real OBD-II protocol scan cycle: interrogating standard ECU addresses
            val dtcList = obdManager.getActiveTroubleCodes()
            _scannedCodes.value = dtcList
            _isScanning.value = false

            // Save the diagnostic session dynamically in Room
            val codeString = dtcList.joinToString(",") { it.code }
            val overview = if (dtcList.isEmpty()) {
                "Vehicle operating status healthy. Zero trouble codes intercepted."
            } else {
                "${dtcList.size} codes caught: ${dtcList.joinToString { it.code }} (${dtcList.first().description})"
            }

            val scan = DiagnosticScan(
                vehicleId = vehicle.id,
                totalDtcCount = dtcList.size,
                codesFound = codeString,
                overview = overview,
                odometerReading = liveSensorData.value.odometer.toInt().takeIf { it > 0 } ?: vehicle.odometer
            )
            val scanId = repository.insertScan(scan)

            // Persist codes locally into the historical timeline record
            dtcList.forEach { code ->
                _isAiLoading.value = true
                val aiReport = aiAdvisor.diagnoseVehicleIssues(
                    vehicleName = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                    dtcCodes = listOf(code.code),
                    sensorSnapshot = liveSensorData.value,
                    aiProviderManager = aiProviderManager
                )

                _aiAnalysisReport.value = aiReport

                repository.insertDtcRecord(
                    DtcRecord(
                        vehicleId = vehicle.id,
                        scanId = scanId.toInt(),
                        code = code.code,
                        description = code.description,
                        severity = code.severity,
                        likelyCauses = code.causes.joinToString("|"),
                        recommendations = code.recommendations.joinToString("|"),
                        fullAiAnalysis = aiReport
                    )
                )
                _isAiLoading.value = false
            }

            // Trigger full batch AI Advisory if multiple codes present
            if (dtcList.size > 1) {
                _isAiLoading.value = true
                val fullReport = aiAdvisor.diagnoseVehicleIssues(
                    vehicleName = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                    dtcCodes = dtcList.map { it.code },
                    sensorSnapshot = liveSensorData.value,
                    aiProviderManager = aiProviderManager
                )
                _aiAnalysisReport.value = fullReport
                _isAiLoading.value = false
            }

            refreshVehicleHistory(vehicle.id)
        }
    }

    fun clearTroubleCodesOnEcu() {
        val vehicle = _selectedVehicle.value ?: return
        obdManager.clearTroubleCodes()
        _scannedCodes.value = emptyList()
        _aiAnalysisReport.value = null

        // Add a scan representing "Codes Cleared"
        viewModelScope.launch {
            val scan = DiagnosticScan(
                vehicleId = vehicle.id,
                totalDtcCount = 0,
                codesFound = "",
                overview = "ECU reset initiated. Diagnostic trouble memory cleared.",
                odometerReading = liveSensorData.value.odometer.toInt().takeIf { it > 0 } ?: vehicle.odometer
            )
            repository.insertScan(scan)
            refreshVehicleHistory(vehicle.id)
        }
    }

    /**
     * Delete an individual historical scan log
     */
    fun deleteHistoricalScan(scan: DiagnosticScan) {
        viewModelScope.launch {
            repository.deleteScan(scan)
            _selectedVehicle.value?.let { refreshVehicleHistory(it.id) }
        }
    }

    // Factory companion for easy ViewModel creation inside Compose
    companion object {
        class Factory(
            private val application: Application,
            private val repository: MekanikRepository
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MekanikViewModel::class.java)) {
                    return MekanikViewModel(application, repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
