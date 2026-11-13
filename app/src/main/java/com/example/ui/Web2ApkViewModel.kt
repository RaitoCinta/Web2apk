package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apkbuilder.ApkBuilderEngine
import com.example.apkbuilder.AxmlManifestWriter
import com.example.data.AppDatabase
import com.example.data.ApkRepository
import com.example.data.BuiltApkEntity
import com.example.model.ApkEntrySummary
import com.example.model.BuildResult
import com.example.model.BuildStepProgress
import com.example.model.IconColorPresets
import com.example.model.PermissionCategory
import com.example.model.PresetPermissions
import com.example.model.ScreenOrientation
import com.example.model.SourceType
import com.example.model.ValidationUtils
import com.example.model.Web2ApkBuildRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BuildState {
  data object Idle : BuildState

  data class Building(val progress: BuildStepProgress) : BuildState

  data class Success(val result: BuildResult) : BuildState

  data class Error(val message: String) : BuildState
}

data class InspectorState(
  val entity: BuiltApkEntity,
  val entries: List<ApkEntrySummary>,
  val isLoading: Boolean = false,
)

data class Web2ApkUiState(
  val appName: String = "",
  val packageName: String = "",
  val packageAutoEdited: Boolean = false,
  val versionName: String = "1.0.0",
  val versionCode: String = "1",
  val sourceType: SourceType = SourceType.URL,
  val webUrl: String = "https://",
  val localFileUri: String? = null,
  val localFileName: String? = null,
  val localFileBytes: ByteArray? = null,
  val localFileError: String? = null,
  val selectedPermissions: Set<String> =
    setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
  val customPermissions: List<String> = emptyList(),
  val customPermissionInput: String = "",
  val customPermissionError: String? = null,
  val selectedPermissionFilter: PermissionCategory? = null,
  val jsEnabled: Boolean = true,
  val domStorageEnabled: Boolean = true,
  val fullscreen: Boolean = false,
  val zoomEnabled: Boolean = false,
  val orientation: ScreenOrientation = ScreenOrientation.AUTO,
  val iconPresetId: String = IconColorPresets.all.first().id,
  val customIconUri: String? = null,
  val customIconBytes: ByteArray? = null,
  val showValidationErrors: Boolean = false,
  val buildState: BuildState = BuildState.Idle,
  val historySearchQuery: String = "",
  val inspectorState: InspectorState? = null,
  val showWebPreview: Boolean = false,
  val userMessage: String? = null,
) {
  val appNameError: String?
    get() = if (showValidationErrors) ValidationUtils.validateAppName(appName) else null

  val packageNameError: String?
    get() = if (showValidationErrors) ValidationUtils.validatePackageName(packageName) else null

  val sourceError: String?
    get() {
      if (!showValidationErrors) return localFileError
      return when (sourceType) {
        SourceType.URL -> ValidationUtils.validateWebUrl(webUrl)
        SourceType.LOCAL_FILE ->
          localFileError
            ?: if (localFileBytes == null) "Pilih file .html atau .zip terlebih dahulu." else null
      }
    }

  val permissionError: String?
    get() =
      if (showValidationErrors) {
        ValidationUtils.validatePermissions(selectedPermissions.toList())
      } else {
        null
      }

  val internetMissingWarning: Boolean
    get() =
      sourceType == SourceType.URL && "android.permission.INTERNET" !in selectedPermissions

  fun toBuildRequest(): Web2ApkBuildRequest {
    val resolvedUrl =
      if (sourceType == SourceType.URL) webUrl.trim() else "file:///android_asset/web/index.html"
    return Web2ApkBuildRequest(
      appName = appName,
      packageName = packageName,
      versionName = versionName.ifBlank { "1.0.0" },
      versionCode = versionCode.toIntOrNull()?.coerceAtLeast(1) ?: 1,
      sourceType = sourceType,
      webUrl = resolvedUrl,
      localFileUri = localFileUri,
      localFileName = localFileName,
      localFileBytes = localFileBytes,
      permissions = selectedPermissions.toList(),
      jsEnabled = jsEnabled,
      domStorageEnabled = domStorageEnabled,
      fullscreen = fullscreen,
      zoomEnabled = zoomEnabled,
      orientation = orientation,
      customIconBytes = customIconBytes,
      iconPresetId = iconPresetId,
    )
  }

  fun manifestPreviewXml(): String {
    val req = toBuildRequest()
    val url =
      if (sourceType == SourceType.URL) webUrl.trim().ifEmpty { "https://example.com" }
      else "file:///android_asset/web/index.html"
    return AxmlManifestWriter.generateReadableManifest(req, url)
  }
}

class Web2ApkViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: ApkRepository
  private val builderEngine: ApkBuilderEngine

  init {
    val db = AppDatabase.getInstance(application)
    repository = ApkRepository(db.builtApkDao())
    builderEngine = ApkBuilderEngine(application, repository)
  }

  private val _uiState = MutableStateFlow(Web2ApkUiState())
  val uiState: StateFlow<Web2ApkUiState> = _uiState.asStateFlow()

  val builtApks: StateFlow<List<BuiltApkEntity>> =
    repository.allBuiltApks.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun updateAppName(value: String) {
    _uiState.update { state ->
      val nextPkg =
        if (!state.packageAutoEdited && value.isNotBlank()) {
          ValidationUtils.suggestPackageName(value)
        } else {
          state.packageName
        }
      state.copy(appName = value, packageName = nextPkg)
    }
  }

  fun updatePackageName(value: String) {
    _uiState.update {
      it.copy(
        packageName = value.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '_' },
        packageAutoEdited = true,
      )
    }
  }

  fun autoSuggestPackageName() {
    _uiState.update { state ->
      state.copy(
        packageName = ValidationUtils.suggestPackageName(state.appName),
        packageAutoEdited = false,
      )
    }
  }

  fun updateVersionName(value: String) {
    _uiState.update { it.copy(versionName = value.take(16)) }
  }

  fun updateVersionCode(value: String) {
    _uiState.update { it.copy(versionCode = value.filter { ch -> ch.isDigit() }.take(6)) }
  }

  fun updateSourceType(type: SourceType) {
    _uiState.update { it.copy(sourceType = type) }
  }

  fun updateWebUrl(value: String) {
    _uiState.update { it.copy(webUrl = value) }
  }

  fun handlePickedSourceFile(uri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val result =
        withContext(Dispatchers.IO) {
          val resolver = context.contentResolver
          var displayName = "web_bundle.html"
          resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
              displayName = cursor.getString(idx) ?: displayName
            }
          }
          val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
          displayName to bytes
        }

      val (fileName, bytes) = result
      val lower = fileName.lowercase()
      when {
        bytes == null || bytes.isEmpty() -> {
          _uiState.update {
            it.copy(localFileError = "Gagal membaca file atau file kosong.", localFileBytes = null)
          }
        }
        !lower.endsWith(".html") && !lower.endsWith(".htm") && !lower.endsWith(".zip") -> {
          _uiState.update {
            it.copy(
              localFileError = "Format harus .html, .htm, atau .zip.",
              localFileName = fileName,
              localFileBytes = null,
            )
          }
        }
        bytes.size > 25 * 1024 * 1024 -> {
          _uiState.update {
            it.copy(
              localFileError = "Ukuran file maksimal 25 MB.",
              localFileName = fileName,
              localFileBytes = null,
            )
          }
        }
        else -> {
          _uiState.update {
            it.copy(
              sourceType = SourceType.LOCAL_FILE,
              localFileUri = uri.toString(),
              localFileName = fileName,
              localFileBytes = bytes,
              localFileError = null,
            )
          }
        }
      }
    }
  }

  fun clearLocalFile() {
    _uiState.update {
      it.copy(localFileUri = null, localFileName = null, localFileBytes = null, localFileError = null)
    }
  }

  fun handlePickedIcon(uri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val bytes =
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
      if (bytes != null && bytes.isNotEmpty()) {
        _uiState.update { it.copy(customIconUri = uri.toString(), customIconBytes = bytes) }
      }
    }
  }

  fun selectIconPreset(presetId: String) {
    _uiState.update { it.copy(iconPresetId = presetId, customIconUri = null, customIconBytes = null) }
  }

  fun togglePermission(permission: String) {
    _uiState.update { state ->
      val next = state.selectedPermissions.toMutableSet()
      if (!next.add(permission)) {
        next.remove(permission)
      }
      state.copy(selectedPermissions = next)
    }
  }

  fun updatePermissionFilter(category: PermissionCategory?) {
    _uiState.update { it.copy(selectedPermissionFilter = category) }
  }

  fun updateCustomPermissionInput(value: String) {
    _uiState.update { it.copy(customPermissionInput = value, customPermissionError = null) }
  }

  fun addCustomPermission() {
    val raw = _uiState.value.customPermissionInput.trim()
    val formatted =
      if (!raw.contains(".") && raw.isNotEmpty()) "android.permission.${raw.uppercase()}" else raw
    val error = ValidationUtils.validateCustomPermission(formatted)
    if (error != null) {
      _uiState.update { it.copy(customPermissionError = error) }
      return
    }
    _uiState.update { state ->
      val isPreset = PresetPermissions.all.any { it.permission == formatted }
      val nextCustom =
        if (!isPreset && formatted !in state.customPermissions) {
          state.customPermissions + formatted
        } else {
          state.customPermissions
        }
      state.copy(
        customPermissions = nextCustom,
        selectedPermissions = state.selectedPermissions + formatted,
        customPermissionInput = "",
        customPermissionError = null,
      )
    }
  }

  fun toggleJs(enabled: Boolean) {
    _uiState.update { it.copy(jsEnabled = enabled) }
  }

  fun toggleDomStorage(enabled: Boolean) {
    _uiState.update { it.copy(domStorageEnabled = enabled) }
  }

  fun toggleFullscreen(enabled: Boolean) {
    _uiState.update { it.copy(fullscreen = enabled) }
  }

  fun toggleZoom(enabled: Boolean) {
    _uiState.update { it.copy(zoomEnabled = enabled) }
  }

  fun updateOrientation(orientation: ScreenOrientation) {
    _uiState.update { it.copy(orientation = orientation) }
  }

  fun updateHistorySearch(query: String) {
    _uiState.update { it.copy(historySearchQuery = query) }
  }

  fun openWebPreview() {
    _uiState.update { it.copy(showWebPreview = true) }
  }

  fun closeWebPreview() {
    _uiState.update { it.copy(showWebPreview = false) }
  }

  fun buildApk() {
    val current = _uiState.value
    val appErr = ValidationUtils.validateAppName(current.appName)
    val pkgErr = ValidationUtils.validatePackageName(current.packageName)
    val srcErr =
      when (current.sourceType) {
        SourceType.URL -> ValidationUtils.validateWebUrl(current.webUrl)
        SourceType.LOCAL_FILE ->
          if (current.localFileBytes == null) "Pilih file .html atau .zip terlebih dahulu."
          else null
      }
    val permErr = ValidationUtils.validatePermissions(current.selectedPermissions.toList())

    if (appErr != null || pkgErr != null || srcErr != null || permErr != null) {
      _uiState.update {
        it.copy(
          showValidationErrors = true,
          userMessage = listOfNotNull(appErr, pkgErr, srcErr, permErr).first(),
        )
      }
      return
    }

    val request = current.toBuildRequest()
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          showValidationErrors = false,
          buildState =
            BuildState.Building(
              BuildStepProgress(1, 6, "Memulai konversi Web2APK...", listOf("Mempersiapkan pipeline..."))
            ),
        )
      }
      try {
        val result =
          builderEngine.buildApk(request) { progress ->
            _uiState.update { it.copy(buildState = BuildState.Building(progress)) }
          }
        _uiState.update { it.copy(buildState = BuildState.Success(result)) }
      } catch (t: Throwable) {
        _uiState.update {
          it.copy(
            buildState =
              BuildState.Error(t.message ?: "Terjadi kesalahan saat mengemas APK.")
          )
        }
      }
    }
  }

  fun dismissBuildState() {
    _uiState.update { it.copy(buildState = BuildState.Idle) }
  }

  fun inspectHistoryItem(entity: BuiltApkEntity) {
    viewModelScope.launch {
      _uiState.update { it.copy(inspectorState = InspectorState(entity, emptyList(), isLoading = true)) }
      val entries = repository.inspectApkEntries(entity.apkFilePath)
      _uiState.update { it.copy(inspectorState = InspectorState(entity, entries, isLoading = false)) }
    }
  }

  fun dismissInspector() {
    _uiState.update { it.copy(inspectorState = null) }
  }

  fun loadFromHistory(entity: BuiltApkEntity) {
    val perms =
      entity.permissionsCsv
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val customPerms =
      perms.filter { perm -> PresetPermissions.all.none { it.permission == perm } }
    val sourceType =
      if (entity.sourceType == SourceType.LOCAL_FILE.name) SourceType.LOCAL_FILE else SourceType.URL

    _uiState.update { state ->
      state.copy(
        appName = entity.appName,
        packageName = entity.packageName,
        packageAutoEdited = true,
        versionName = entity.versionName,
        versionCode = entity.versionCode.toString(),
        sourceType = sourceType,
        webUrl = if (sourceType == SourceType.URL) entity.sourceValue else state.webUrl,
        selectedPermissions = perms,
        customPermissions = (state.customPermissions + customPerms).distinct(),
        jsEnabled = entity.jsEnabled,
        domStorageEnabled = entity.domStorageEnabled,
        fullscreen = entity.fullscreen,
        zoomEnabled = entity.zoomEnabled,
        orientation =
          ScreenOrientation.entries.firstOrNull { it.name == entity.orientation }
            ?: ScreenOrientation.AUTO,
        userMessage = "Konfigurasi '${entity.appName}' dimuat ke Builder.",
      )
    }
  }

  fun deleteHistoryItem(entity: BuiltApkEntity) {
    viewModelScope.launch {
      repository.deleteBuiltApk(entity)
      _uiState.update { it.copy(userMessage = "APK '${entity.apkFileName}' dihapus.") }
    }
  }

  fun exportApkToUri(apkFilePath: String, destinationUri: Uri) {
    viewModelScope.launch {
      val ok =
        withContext(Dispatchers.IO) {
          val src = File(apkFilePath)
          if (!src.exists()) return@withContext false
          val resolver = getApplication<Application>().contentResolver
          resolver.openOutputStream(destinationUri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
          }
          true
        }
      _uiState.update {
        it.copy(
          userMessage =
            if (ok) "File APK berhasil disimpan!" else "File APK sumber tidak ditemukan."
        )
      }
    }
  }

  fun clearUserMessage() {
    _uiState.update { it.copy(userMessage = null) }
  }
}
