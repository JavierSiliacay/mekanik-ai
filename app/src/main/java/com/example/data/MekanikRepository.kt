package com.example.data

import kotlinx.coroutines.flow.Flow

class MekanikRepository(
    private val vehicleDao: VehicleDao,
    private val diagnosticScanDao: DiagnosticScanDao,
    private val dtcRecordDao: DtcRecordDao
) {
    val allVehicles: Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    fun getScansForVehicle(vehicleId: Int): Flow<List<DiagnosticScan>> {
        return diagnosticScanDao.getScansForVehicle(vehicleId)
    }

    fun getHistoryForVehicle(vehicleId: Int): Flow<List<DtcRecord>> {
        return dtcRecordDao.getDtcHistoryForVehicle(vehicleId)
    }

    fun getDtcRecordsForScan(scanId: Int): Flow<List<DtcRecord>> {
        return dtcRecordDao.getDtcRecordsForScan(scanId)
    }

    suspend fun getVehicleById(id: Int): Vehicle? = vehicleDao.getVehicleById(id)

    suspend fun insertVehicle(vehicle: Vehicle): Long = vehicleDao.insertVehicle(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.updateVehicle(vehicle)

    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.deleteVehicle(vehicle)

    suspend fun insertScan(scan: DiagnosticScan): Long = diagnosticScanDao.insertScan(scan)

    suspend fun insertDtcRecord(record: DtcRecord): Long = dtcRecordDao.insertDtcRecord(record)

    suspend fun deleteScan(scan: DiagnosticScan) {
        dtcRecordDao.deleteDtcRecordsForScan(scan.id)
        diagnosticScanDao.deleteScan(scan)
    }
}
