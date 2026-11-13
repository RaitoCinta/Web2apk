package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BuiltApkEntity
import com.example.model.SourceType
import com.example.model.ValidationUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
  apks: List<BuiltApkEntity>,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onSaveApk: (BuiltApkEntity) -> Unit,
  onShareApk: (BuiltApkEntity) -> Unit,
  onInspectApk: (BuiltApkEntity) -> Unit,
  onLoadToBuilder: (BuiltApkEntity) -> Unit,
  onDeleteApk: (BuiltApkEntity) -> Unit,
  onGoToBuilder: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val filtered =
    remember(apks, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) apks
      else
        apks.filter {
          it.appName.lowercase().contains(q) ||
            it.packageName.lowercase().contains(q) ||
            it.sourceValue.lowercase().contains(q)
        }
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
      if (apks.isNotEmpty()) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          placeholder = { Text("Cari nama aplikasi, package, atau URL...") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp)
              .testTag("history_search_input"),
        )
      }

      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier.size(76.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Filled.Android,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(40.dp),
                )
              }
            }
            Spacer(Modifier.height(16.dp))
            Text(
              text = stringResource(R.string.empty_history_title),
              style = MaterialTheme.typography.headlineMedium,
              textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
              text = stringResource(R.string.empty_history_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGoToBuilder, modifier = Modifier.testTag("empty_goto_builder")) {
              Text("Buat APK Pertama")
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(filtered, key = { it.id }) { item ->
            BuiltApkCard(
              item = item,
              onSaveApk = { onSaveApk(item) },
              onShareApk = { onShareApk(item) },
              onInspectApk = { onInspectApk(item) },
              onLoadToBuilder = { onLoadToBuilder(item) },
              onDeleteApk = { onDeleteApk(item) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BuiltApkCard(
  item: BuiltApkEntity,
  onSaveApk: () -> Unit,
  onShareApk: () -> Unit,
  onInspectApk: () -> Unit,
  onLoadToBuilder: () -> Unit,
  onDeleteApk: () -> Unit,
) {
  val dateStr =
    remember(item.createdAtMillis) {
      SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(item.createdAtMillis))
    }
  val permCount = item.permissionsCsv.split(",").count { it.isNotBlank() }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth().testTag("apk_history_card_${item.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.Android,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.appName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = item.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onDeleteApk) {
          Icon(
            Icons.Filled.DeleteOutline,
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataBadge(
          if (item.sourceType == SourceType.URL.name) "URL" else "FILE",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
        MetadataBadge(
          "v${item.versionName} (${item.versionCode})",
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetadataBadge(
          "$permCount izin",
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetadataBadge(
          ValidationUtils.formatFileSize(item.fileSizeBytes),
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      Spacer(Modifier.height(8.dp))

      Text(
        text = item.sourceValue,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "Dibuat $dateStr • SHA256: ${item.sha256Hex.take(16)}...",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onSaveApk,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Simpan", maxLines = 1)
        }
        OutlinedButton(
          onClick = onShareApk,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Bagikan", maxLines = 1)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        TextButton(onClick = onInspectApk) {
          Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.action_inspect_apk))
        }
        TextButton(onClick = onLoadToBuilder) {
          Icon(
            Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.action_load_template))
        }
      }
    }
  }
}

@Composable
private fun MetadataBadge(
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
) {
  Surface(color = containerColor, shape = RoundedCornerShape(6.dp)) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}
