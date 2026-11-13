package com.example.data

import com.example.model.ApkEntrySummary
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ApkRepository(private val dao: BuiltApkDao) {
  val allBuiltApks: Flow<List<BuiltApkEntity>> = dao.getAllBuiltApks()

  suspend fun saveBuiltApk(entity: BuiltApkEntity): Long = dao.insertBuiltApk(entity)

  suspend fun getById(id: Long): BuiltApkEntity? = dao.getBuiltApkById(id)

  suspend fun deleteBuiltApk(entity: BuiltApkEntity) {
    withContext(Dispatchers.IO) {
      val file = File(entity.apkFilePath)
      if (file.exists()) {
        file.delete()
      }
      dao.deleteBuiltApkById(entity.id)
    }
  }

  suspend fun inspectApkEntries(apkPath: String): List<ApkEntrySummary> =
    withContext(Dispatchers.IO) {
      val file = File(apkPath)
      if (!file.exists()) return@withContext emptyList()
      val entries = mutableListOf<ApkEntrySummary>()
      ZipFile(file).use { zip ->
        val enumEntries = zip.entries()
        while (enumEntries.hasMoreElements()) {
          val entry = enumEntries.nextElement()
          if (!entry.isDirectory) {
            entries.add(
              ApkEntrySummary(
                path = entry.name,
                compressedSize = entry.compressedSize.coerceAtLeast(0L),
                uncompressedSize = entry.size.coerceAtLeast(0L),
                crc32Hex = "%08X".format(entry.crc),
              )
            )
          }
        }
      }
      entries.sortedBy { it.path }
    }
}
