package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "built_apks")
data class BuiltApkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Int,
  val sourceType: String,
  val sourceValue: String,
  val permissionsCsv: String,
  val jsEnabled: Boolean,
  val domStorageEnabled: Boolean,
  val fullscreen: Boolean,
  val zoomEnabled: Boolean,
  val orientation: String,
  val apkFileName: String,
  val apkFilePath: String,
  val fileSizeBytes: Long,
  val sha256Hex: String,
  val signatureScheme: String,
  val createdAtMillis: Long = System.currentTimeMillis(),
)
