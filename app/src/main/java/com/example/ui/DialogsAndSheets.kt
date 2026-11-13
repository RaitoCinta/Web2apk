package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.ApkEntrySummary
import com.example.model.BuildResult
import com.example.model.SourceType
import com.example.model.ValidationUtils
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun BuildStatusModal(
  buildState: BuildState,
  onSaveApk: (BuildResult) -> Unit,
  onShareApk: (BuildResult) -> Unit,
  onInspectApk: (BuildResult) -> Unit,
  onDismiss: () -> Unit,
) {
  when (buildState) {
    BuildState.Idle -> Unit
    is BuildState.Building -> {
      val progress = buildState.progress
      Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
      ) {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.fillMaxWidth().testTag("build_progress_dialog"),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
              Spacer(Modifier.width(12.dp))
              Column {
                Text("Membangun File APK...", style = MaterialTheme.typography.titleLarge)
                Text(
                  "Langkah ${progress.currentStep}/${progress.totalSteps}: ${progress.title}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
              progress = { progress.currentStep.toFloat() / progress.totalSteps.toFloat() },
              modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Surface(
              color = Color(0xFF0A0E17),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 200.dp),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                progress.logs.forEach { line ->
                  Text(
                    text = "> $line",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF34D399),
                        fontFamily = JetBrainsMonoFontFamily,
                      ),
                    modifier = Modifier.padding(vertical = 2.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
    is BuildState.Success -> {
      val res = buildState.result
      Dialog(onDismissRequest = onDismiss) {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth().testTag("build_success_dialog"),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
              )
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("APK Berhasil Dibuat!", style = MaterialTheme.typography.titleLarge)
                Text(
                  res.fileName,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                InfoPair("Aplikasi", "${res.appName} (v${res.versionName})")
                InfoPair("Package", res.packageName)
                InfoPair("Ukuran APK", ValidationUtils.formatFileSize(res.fileSizeBytes))
                InfoPair("Permission", "${res.permissionsCount} izin manifest")
                InfoPair("Entri ZIP", "${res.entries.size} file terkemas")
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    res.signatureScheme,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text(
                  "SHA-256: ${res.sha256Hex}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Spacer(Modifier.height(16.dp))

            Button(
              onClick = { onSaveApk(res) },
              modifier = Modifier.fillMaxWidth().testTag("save_apk_button"),
            ) {
              Icon(Icons.Filled.SaveAlt, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.action_save_apk))
            }

            Spacer(Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onShareApk(res) },
                modifier = Modifier.weight(1f).testTag("share_apk_button"),
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bagikan", maxLines = 1)
              }
              OutlinedButton(
                onClick = { onInspectApk(res) },
                modifier = Modifier.weight(1f).testTag("inspect_apk_button"),
              ) {
                Icon(
                  Icons.Filled.FolderZip,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Isi APK", maxLines = 1)
              }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
              Text("Tutup")
            }
          }
        }
      }
    }
    is BuildState.Error -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
          Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        title = { Text("Konversi Gagal") },
        text = { Text(buildState.message) },
        confirmButton = { Button(onClick = onDismiss) { Text("Mengerti") } },
      )
    }
  }
}

@Composable
private fun InfoPair(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(8.dp))
    Text(
      value,
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          fontFamily = JetBrainsMonoFontFamily,
        ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun ApkInspectorDialog(inspectorState: InspectorState, onDismiss: () -> Unit) {
  BackHandler(onBack = onDismiss)
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp).testTag("apk_inspector_dialog"),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.FolderZip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Inspektor Paket APK", style = MaterialTheme.typography.titleLarge)
            Text(
              inspectorState.entity.apkFileName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup")
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        if (inspectorState.isLoading) {
          Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        } else {
          Text(
            "Menampilkan ${inspectorState.entries.size} file di dalam arsip .apk:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(inspectorState.entries, key = { it.path }) { entry ->
              ApkEntryRow(entry)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ApkEntryRow(entry: ApkEntrySummary) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.path,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "CRC32: ${entry.crc32Hex}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(8.dp))
      Text(
        text = ValidationUtils.formatFileSize(entry.uncompressedSize),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewDialog(state: Web2ApkUiState, onDismiss: () -> Unit) {
  BackHandler(onBack = onDismiss)
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pageTitle by remember { mutableStateOf("Pratinjau WebView") }
  var isLoading by remember { mutableStateOf(true) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize().testTag("web_preview_dialog"),
      topBar = {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_back))
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = pageTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (state.sourceType == SourceType.URL) state.webUrl
                  else state.localFileName ?: "HTML Lokal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(onClick = { webViewRef?.reload() }) {
              Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang")
            }
          }
        }
      },
    ) { innerPadding ->
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        AndroidView(
          factory = { ctx ->
            WebView(ctx).apply {
              settings.javaScriptEnabled = state.jsEnabled
              settings.domStorageEnabled = state.domStorageEnabled
              settings.builtInZoomControls = state.zoomEnabled
              settings.displayZoomControls = false
              webViewClient =
                object : WebViewClient() {
                  override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    pageTitle = view?.title?.ifBlank { "Pratinjau WebView" } ?: "Pratinjau WebView"
                  }
                }
              webChromeClient =
                object : WebChromeClient() {
                  override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
                  }
                }
              if (state.sourceType == SourceType.URL) {
                val target =
                  if (state.webUrl.startsWith("http")) state.webUrl.trim()
                  else "https://${state.webUrl.trim()}"
                loadUrl(target)
              } else {
                val htmlText =
                  state.localFileBytes?.toString(Charsets.UTF_8)
                    ?: "<html><body><h3>Tidak ada konten HTML</h3></body></html>"
                loadDataWithBaseURL(null, htmlText, "text/html", "UTF-8", null)
              }
              webViewRef = this
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
        if (isLoading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
      }
    }
  }
}
