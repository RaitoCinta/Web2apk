package com.example.apkbuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.ApkRepository
import com.example.data.BuiltApkEntity
import com.example.model.BuildResult
import com.example.model.BuildStepProgress
import com.example.model.IconColorPresets
import com.example.model.SourceType
import com.example.model.Web2ApkBuildRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApkBuilderEngine(private val context: Context, private val repository: ApkRepository) {

  suspend fun buildApk(
    request: Web2ApkBuildRequest,
    onProgress: (BuildStepProgress) -> Unit,
  ): BuildResult =
    withContext(Dispatchers.IO) {
      val logs = mutableListOf<String>()
      fun emit(step: Int, title: String, log: String) {
        logs += log
        onProgress(BuildStepProgress(step, 6, title, logs.toList()))
      }

      emit(1, "Menyiapkan aset web & konfigurasi", "Memvalidasi sumber konten...")
      val assetFiles = linkedMapOf<String, ByteArray>()
      val resolvedTargetUrl: String

      if (request.sourceType == SourceType.URL) {
        resolvedTargetUrl = request.webUrl.trim()
        logs[logs.lastIndex] = "Sumber URL terhubung: $resolvedTargetUrl"
      } else {
        val rawBytes =
          request.localFileBytes
            ?: throw IllegalArgumentException("File HTML atau ZIP belum dipilih.")
        val fileName = request.localFileName?.lowercase() ?: "index.html"
        if (fileName.endsWith(".zip")) {
          var foundIndex = false
          var firstHtmlPath: String? = null
          ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
              if (!entry.isDirectory) {
                val safeName = entry.name.replace("\\", "/").trimStart('/')
                if (!safeName.contains("..") && safeName.isNotBlank()) {
                  val entryBytes = zis.readBytes()
                  assetFiles["assets/web/$safeName"] = entryBytes
                  if (safeName.equals("index.html", ignoreCase = true)) {
                    foundIndex = true
                  } else if (firstHtmlPath == null && safeName.endsWith(".html", ignoreCase = true)) {
                    firstHtmlPath = safeName
                  }
                }
              }
              entry = zis.nextEntry
            }
          }
          if (assetFiles.isEmpty()) {
            throw IllegalArgumentException("Arsip ZIP kosong atau tidak valid.")
          }
          val entryHtml =
            when {
              foundIndex -> "index.html"
              firstHtmlPath != null -> firstHtmlPath!!
              else ->
                throw IllegalArgumentException(
                  "Arsip ZIP tidak memiliki file .html (misalnya index.html)."
                )
            }
          resolvedTargetUrl = "file:///android_asset/web/$entryHtml"
          emit(
            1,
            "Menyiapkan aset web & konfigurasi",
            "Mengekstrak ${assetFiles.size} file dari ${request.localFileName} (entry: $entryHtml)",
          )
        } else {
          assetFiles["assets/web/index.html"] = rawBytes
          resolvedTargetUrl = "file:///android_asset/web/index.html"
          emit(
            1,
            "Menyiapkan aset web & konfigurasi",
            "Mengemas file HTML lokal (${rawBytes.size} byte) -> assets/web/index.html",
          )
        }
      }

      val configJson =
        """
        {
          "appName": "${request.appName.trim().replace("\"", "\\\"")}",
          "packageName": "${request.packageName.trim()}",
          "versionName": "${request.versionName.trim()}",
          "versionCode": ${request.versionCode},
          "targetUrl": "${resolvedTargetUrl.replace("\"", "\\\"")}",
          "jsEnabled": ${request.jsEnabled},
          "domStorageEnabled": ${request.domStorageEnabled},
          "fullscreen": ${request.fullscreen},
          "zoomEnabled": ${request.zoomEnabled},
          "orientation": "${request.orientation.name}",
          "permissions": [${request.permissions.joinToString(",") { "\"$it\"" }}]
        }
        """
          .trimIndent()
      assetFiles["assets/web2apk_config.json"] = configJson.toByteArray(Charsets.UTF_8)

      emit(
        2,
        "Mengompilasi AndroidManifest.xml (AXML)",
        "Menyusun ${request.permissions.size} permission untuk ${request.packageName.trim()}...",
      )
      val manifestBytes = AxmlManifestWriter.compileBinaryManifest(request, resolvedTargetUrl)

      emit(
        3,
        "Membangun resources.arsc & ikon peluncur",
        "Merender ic_launcher.png (192x192) & tabel sumber daya...",
      )
      val resourcesBytes =
        ResourcesArscWriter.compileResources(
          packageName = request.packageName.trim(),
          appName = request.appName.trim(),
        )
      val iconPngBytes = buildLauncherIconPng(request)

      emit(
        4,
        "Menghasilkan classes.dex (Dalvik WebView)",
        "Mengompilasi WebAppActivity ke bytecode dex\\n035...",
      )
      val dexBytes =
        MinimalWebViewDexWriter.buildWebViewDex(
          targetUrl = resolvedTargetUrl,
          jsEnabled = request.jsEnabled,
          domStorageEnabled = request.domStorageEnabled,
          fullscreen = request.fullscreen,
          zoomEnabled = request.zoomEnabled,
        )

      emit(5, "Mengemas arsip APK & menyelaraskan entri", "Menggabungkan biner ke kontainer ZIP...")
      val unsignedEntries = linkedMapOf<String, ByteArray>()
      unsignedEntries["AndroidManifest.xml"] = manifestBytes
      unsignedEntries["classes.dex"] = dexBytes
      unsignedEntries["resources.arsc"] = resourcesBytes
      unsignedEntries["res/mipmap-xxxhdpi-v4/ic_launcher.png"] = iconPngBytes
      unsignedEntries.putAll(assetFiles)

      emit(
        6,
        "Menandatangani APK (RSA-2048 v1 + v2)",
        "Menghasilkan sertifikat X.509 & APK Signature Scheme v2...",
      )
      val creds = ApkSigner.generateSigningCredentials(request.appName.trim())
      val manifestMf = ApkSigner.buildJarV1Manifest(unsignedEntries)
      val certSf = ApkSigner.buildJarV1SignatureFile(manifestMf, unsignedEntries)
      val certRsa = ApkSigner.buildPkcs7SignatureBlock(certSf, creds)

      val outDir = File(context.filesDir, "built_apks").apply { mkdirs() }
      val slug =
        request.appName
          .trim()
          .lowercase()
          .replace(Regex("[^a-z0-9]+"), "_")
          .trim('_')
          .ifEmpty { "web2apk_app" }
      val safeVersion = request.versionName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val apkFileName = "${slug}_v${safeVersion}_${System.currentTimeMillis() % 10000}.apk"
      val outFile = File(outDir, apkFileName)

      ZipOutputStream(FileOutputStream(outFile)).use { zos ->
        unsignedEntries.forEach { (path, bytes) ->
          val entry = ZipEntry(path)
          if (path == "resources.arsc" || path.endsWith(".png")) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            val crc = CRC32().apply { update(bytes) }
            entry.crc = crc.value
          }
          zos.putNextEntry(entry)
          zos.write(bytes)
          zos.closeEntry()
        }
        listOf(
            "META-INF/MANIFEST.MF" to manifestMf,
            "META-INF/CERT.SF" to certSf,
            "META-INF/CERT.RSA" to certRsa,
          )
          .forEach { (metaPath, metaBytes) ->
            zos.putNextEntry(ZipEntry(metaPath))
            zos.write(metaBytes)
            zos.closeEntry()
          }
      }

      ApkSigner.applyApkSignatureV2(outFile, creds)

      val sha256Hex =
        MessageDigest.getInstance("SHA-256")
          .digest(outFile.readBytes())
          .joinToString("") { "%02x".format(it) }

      val sourceValue =
        if (request.sourceType == SourceType.URL) request.webUrl.trim()
        else request.localFileName ?: "index.html"

      val entityId =
        repository.saveBuiltApk(
          BuiltApkEntity(
            appName = request.appName.trim(),
            packageName = request.packageName.trim(),
            versionName = request.versionName.trim().ifEmpty { "1.0.0" },
            versionCode = request.versionCode,
            sourceType = request.sourceType.name,
            sourceValue = sourceValue,
            permissionsCsv = request.permissions.joinToString(","),
            jsEnabled = request.jsEnabled,
            domStorageEnabled = request.domStorageEnabled,
            fullscreen = request.fullscreen,
            zoomEnabled = request.zoomEnabled,
            orientation = request.orientation.name,
            apkFileName = apkFileName,
            apkFilePath = outFile.absolutePath,
            fileSizeBytes = outFile.length(),
            sha256Hex = sha256Hex,
            signatureScheme = "JAR v1 + APK Signature v2 (RSA-2048)",
          )
        )

      val entries = repository.inspectApkEntries(outFile.absolutePath)
      emit(6, "Konversi APK selesai!", "File siap: $apkFileName (${outFile.length()} byte)")

      BuildResult(
        entityId = entityId,
        appName = request.appName.trim(),
        packageName = request.packageName.trim(),
        versionName = request.versionName.trim().ifEmpty { "1.0.0" },
        versionCode = request.versionCode,
        apkFilePath = outFile.absolutePath,
        fileName = apkFileName,
        fileSizeBytes = outFile.length(),
        sha256Hex = sha256Hex,
        signatureScheme = "JAR v1 + APK Signature v2 (RSA-2048)",
        permissionsCount = request.permissions.size,
        sourceType = request.sourceType,
        sourceLabel = sourceValue,
        entries = entries,
      )
    }

  private fun buildLauncherIconPng(request: Web2ApkBuildRequest): ByteArray {
    val customBytes = request.customIconBytes
    if (customBytes != null) {
      val decoded = BitmapFactory.decodeByteArray(customBytes, 0, customBytes.size)
      if (decoded != null) {
        val scaled = Bitmap.createScaledBitmap(decoded, 192, 192, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return out.toByteArray()
      }
    }

    val preset =
      IconColorPresets.all.firstOrNull { it.id == request.iconPresetId } ?: IconColorPresets.all.first()
    val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = preset.backgroundHex.toInt()
        style = Paint.Style.FILL
      }
    canvas.drawRoundRect(RectF(8f, 8f, 184f, 184f), 42f, 42f, bgPaint)

    val ringPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = preset.accentHex.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
      }
    canvas.drawRoundRect(RectF(18f, 18f, 174f, 174f), 34f, 34f, ringPaint)

    val initials =
      request.appName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "W2" }

    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = preset.accentHex.toInt()
        textSize = 72f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
      }
    val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(initials, canvas.width / 2f, yPos, textPaint)

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
  }
}
