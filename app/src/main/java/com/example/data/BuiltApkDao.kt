package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuiltApkDao {
  @Query("SELECT * FROM built_apks ORDER BY createdAtMillis DESC")
  fun getAllBuiltApks(): Flow<List<BuiltApkEntity>>

  @Query("SELECT * FROM built_apks WHERE id = :id LIMIT 1")
  suspend fun getBuiltApkById(id: Long): BuiltApkEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBuiltApk(entity: BuiltApkEntity): Long

  @Query("DELETE FROM built_apks WHERE id = :id")
  suspend fun deleteBuiltApkById(id: Long)
}
