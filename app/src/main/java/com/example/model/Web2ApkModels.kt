package com.example.model

enum class SourceType {
  URL,
  LOCAL_FILE,
}

enum class ScreenOrientation(val label: String, val manifestValue: String?) {
  AUTO("Otomatis", null),
  PORTRAIT("Potret", "portrait"),
  LANDSCAPE("Lanskap", "landscape"),
}

enum class PermissionCategory(val label: String) {
  NETWORK("Jaringan"),
  HARDWARE("Kamera & Audio"),
  LOCATION("Lokasi"),
  SYSTEM("Sistem & UI"),
}

data class AndroidPermissionOption(
  val permission: String,
  val shortName: String,
  val title: String,
  val description: String,
  val category: PermissionCategory,
  val isDangerous: Boolean = false,
)

object PresetPermissions {
  val all: List<AndroidPermissionOption> =
    listOf(
      AndroidPermissionOption(
        permission = "android.permission.INTERNET",
        shortName = "INTERNET",
        title = "Akses Internet",
        description = "Memuat halaman web, API, dan konten online.",
        category = PermissionCategory.NETWORK,
        isDangerous = false,
      ),
      AndroidPermissionOption(
        permission = "android.permission.ACCESS_NETWORK_STATE",
        shortName = "ACCESS_NETWORK_STATE",
        title = "Status Jaringan",
        description = "Mendeteksi koneksi Wi-Fi atau seluler aktif.",
        category = PermissionCategory.NETWORK,
        isDangerous = false,
      ),
      AndroidPermissionOption(
        permission = "android.permission.CAMERA",
        shortName = "CAMERA",
        title = "Kamera",
        description = "Akses kamera untuk WebRTC, scan QR, atau unggah foto.",
        category = PermissionCategory.HARDWARE,
        isDangerous = true,
      ),
      AndroidPermissionOption(
        permission = "android.permission.RECORD_AUDIO",
        shortName = "RECORD_AUDIO",
        title = "Mikrofon",
        description = "Rekam suara atau panggilan audio di aplikasi web.",
        category = PermissionCategory.HARDWARE,
        isDangerous = true,
      ),
      AndroidPermissionOption(
        permission = "android.permission.MODIFY_AUDIO_SETTINGS",
        shortName = "MODIFY_AUDIO_SETTINGS",
        title = "Kontrol Audio",
        description = "Mengatur output speaker dan volume media web.",
        category = PermissionCategory.HARDWARE,
        isDangerous = false,
      ),
      AndroidPermissionOption(
        permission = "android.permission.ACCESS_FINE_LOCATION",
        shortName = "ACCESS_FINE_LOCATION",
        title = "Lokasi GPS Presisi",
        description = "Mengakses koordinat GPS akurat untuk Geolocation Web.",
        category = PermissionCategory.LOCATION,
        isDangerous = true,
      ),
      AndroidPermissionOption(
        permission = "android.permission.ACCESS_COARSE_LOCATION",
        shortName = "ACCESS_COARSE_LOCATION",
        title = "Lokasi Perkiraan",
        description = "Mengakses lokasi berbasis jaringan Wi-Fi atau seluler.",
        category = PermissionCategory.LOCATION,
        isDangerous = true,
      ),
      AndroidPermissionOption(
        permission = "android.permission.POST_NOTIFICATIONS",
        shortName = "POST_NOTIFICATIONS",
        title = "Notifikasi",
        description = "Menampilkan notifikasi lokal dan pengingat push.",
        category = PermissionCategory.SYSTEM,
        isDangerous = true,
      ),
      AndroidPermissionOption(
        permission = "android.permission.VIBRATE",
        shortName = "VIBRATE",
        title = "Getar Perangkat",
        description = "Mendukung umpan balik haptik navigator.vibrate().",
        category = PermissionCategory.SYSTEM,
        isDangerous = false,
      ),
      AndroidPermissionOption(
        permission = "android.permission.WAKE_LOCK",
        shortName = "WAKE_LOCK",
        title = "Layar Tetap Menyala",
        description = "Mencegah layar mati otomatis saat memutar konten.",
        category = PermissionCategory.SYSTEM,
        isDangerous = false,
      ),
    )
}

data class IconColorPreset(
  val id: String,
  val label: String,
  val backgroundHex: Long,
  val accentHex: Long,
)

object IconColorPresets {
  val all =
    listOf(
      IconColorPreset("emerald", "Zamrud", 0xFF064E3B, 0xFF34D399),
      IconColorPreset("cyan", "Sian", 0xFF0C4A6E, 0xFF22D3EE),
      IconColorPreset("indigo", "Indigo", 0xFF1E1B4B, 0xFF818CF8),
      IconColorPreset("amber", "Amber", 0xFF451A03, 0xFFFBBF24),
      IconColorPreset("rose", "Karmine", 0xFF4C0519, 0xFFFB7185),
      IconColorPreset("slate", "Grafit", 0xFF0F172A, 0xFF94A3B8),
    )
}

data class Web2ApkBuildRequest(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Int,
  val sourceType: SourceType,
  val webUrl: String,
  val localFileUri: String?,
  val localFileName: String?,
  val localFileBytes: ByteArray?,
  val permissions: List<String>,
  val jsEnabled: Boolean,
  val domStorageEnabled: Boolean,
  val fullscreen: Boolean,
  val zoomEnabled: Boolean,
  val orientation: ScreenOrientation,
  val customIconBytes: ByteArray?,
  val iconPresetId: String,
)

data class BuildStepProgress(
  val currentStep: Int,
  val totalSteps: Int,
  val title: String,
  val logs: List<String>,
)

data class ApkEntrySummary(
  val path: String,
  val compressedSize: Long,
  val uncompressedSize: Long,
  val crc32Hex: String,
)

data class BuildResult(
  val entityId: Long,
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Int,
  val apkFilePath: String,
  val fileName: String,
  val fileSizeBytes: Long,
  val sha256Hex: String,
  val signatureScheme: String,
  val permissionsCount: Int,
  val sourceType: SourceType,
  val sourceLabel: String,
  val entries: List<ApkEntrySummary>,
)

object ValidationUtils {
  private val packageRegex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
  private val javaKeywords =
    setOf(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
      "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
      "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
      "interface", "long", "native", "new", "package", "private", "protected", "public",
      "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
      "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
    )

  fun validateAppName(name: String): String? {
    val trimmed = name.trim()
    return when {
      trimmed.isEmpty() -> "Nama aplikasi wajib diisi."
      trimmed.length < 2 -> "Nama aplikasi minimal 2 karakter."
      trimmed.length > 40 -> "Nama aplikasi maksimal 40 karakter."
      else -> null
    }
  }

  fun validatePackageName(pkg: String): String? {
    val trimmed = pkg.trim()
    if (trimmed.isEmpty()) return "Package name wajib diisi."
    if (!packageRegex.matches(trimmed)) {
      return "Format harus huruf kecil dengan minimal 2 segmen (contoh: com.nama.app)."
    }
    val segments = trimmed.split('.')
    if (segments.any { it in javaKeywords }) {
      return "Package name tidak boleh memakai kata kunci Java."
    }
    return null
  }

  fun suggestPackageName(appName: String): String {
    val slug =
      appName
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
        .take(18)
        .ifEmpty { "myapp" }
    val safeSlug = if (slug.first().isDigit()) "app$slug" else slug
    return "com.web2apk.$safeSlug"
  }

  fun validateWebUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return "Tautan URL web wajib diisi."
    val lower = trimmed.lowercase()
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
      return "URL harus diawali https:// atau http://"
    }
    val hostPart = trimmed.substringAfter("://").substringBefore("/").substringBefore("?")
    if (hostPart.isBlank() || (!hostPart.contains(".") && !hostPart.startsWith("localhost"))) {
      return "Masukkan domain URL yang valid (contoh: https://example.com)."
    }
    return null
  }

  fun validatePermissions(permissions: List<String>): String? {
    if (permissions.isEmpty()) {
      return "Pilih minimal 1 permission Android untuk APK."
    }
    return null
  }

  fun validateCustomPermission(perm: String): String? {
    val trimmed = perm.trim()
    if (trimmed.isEmpty()) return "Nama permission tidak boleh kosong."
    if (!trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$"))) {
      return "Format harus seperti android.permission.CUSTOM_NAME"
    }
    return null
  }

  fun formatFileSize(bytes: Long): String {
    return when {
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
      else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
  }
}
