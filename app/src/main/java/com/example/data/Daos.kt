package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Int): Vehicle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)
}

@Dao
interface DiagnosticScanDao {
    @Query("SELECT * FROM diagnostic_scans WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getScansForVehicle(vehicleId: Int): Flow<List<DiagnosticScan>>

    @Query("SELECT * FROM diagnostic_scans WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Int): DiagnosticScan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: DiagnosticScan): Long

    @Delete
    suspend fun deleteScan(scan: DiagnosticScan)
}

@Dao
interface DtcRecordDao {
    @Query("SELECT * FROM dtc_history WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getDtcHistoryForVehicle(vehicleId: Int): Flow<List<DtcRecord>>

    @Query("SELECT * FROM dtc_history WHERE scanId = :scanId ORDER BY id ASC")
    fun getDtcRecordsForScan(scanId: Int): Flow<List<DtcRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtcRecord(record: DtcRecord): Long

    @Query("DELETE FROM dtc_history WHERE scanId = :scanId")
    suspend fun deleteDtcRecordsForScan(scanId: Int)
}
