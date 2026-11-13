package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.IconColorPresets
import com.example.model.PermissionCategory
import com.example.model.PresetPermissions
import com.example.model.ScreenOrientation
import com.example.model.SourceType
import com.example.model.ValidationUtils
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun BuilderScreen(
  state: Web2ApkUiState,
  viewModel: Web2ApkViewModel,
  modifier: Modifier = Modifier,
) {
  val filePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { viewModel.handlePickedSourceFile(it) }
    }
  val iconPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let { viewModel.handlePickedIcon(it) }
    }

  var showAdvanced by rememberSaveable { mutableStateOf(false) }
  var showManifestPreview by rememberSaveable { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Forge Banner + Readiness Pills
      item { HeroForgeCard(state = state) }

      // Section 1: App Identity & Icon
      item {
        ForgeSectionCard(
          title = stringResource(R.string.section_identity),
          icon = Icons.Filled.Android,
          stepBadge = "1",
        ) {
          AppIconRow(
            appName = state.appName,
            customIconUri = state.customIconUri,
            selectedPresetId = state.iconPresetId,
            onPickImage = {
              iconPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onSelectPreset = viewModel::selectIconPreset,
          )

          Spacer(modifier = Modifier.height(14.dp))

          OutlinedTextField(
            value = state.appName,
            onValueChange = viewModel::updateAppName,
            label = { Text(stringResource(R.string.label_app_name)) },
            placeholder = { Text(stringResource(R.string.hint_app_name)) },
            singleLine = true,
            isError = state.appNameError != null,
            supportingText = state.appNameError?.let { err -> { Text(err) } },
            modifier = Modifier.fillMaxWidth().testTag("app_name_input"),
          )

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = state.packageName,
            onValueChange = viewModel::updatePackageName,
            label = { Text(stringResource(R.string.label_package_name)) },
            placeholder = { Text(stringResource(R.string.hint_package_name)) },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            isError = state.packageNameError != null,
            supportingText =
              state.packageNameError?.let { err -> { Text(err) } }
                ?: {
                  Text(
                    "Identitas unik APK di perangkat Android.",
                    style = MaterialTheme.typography.labelMedium,
                  )
                },
            trailingIcon = {
              TextButton(
                onClick = viewModel::autoSuggestPackageName,
                modifier = Modifier.testTag("auto_package_button"),
              ) {
                Icon(
                  Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Auto")
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("package_name_input"),
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedTextField(
              value = state.versionName,
              onValueChange = viewModel::updateVersionName,
              label = { Text(stringResource(R.string.label_version_name)) },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
              modifier = Modifier.weight(1f).testTag("version_name_input"),
            )
            OutlinedTextField(
              value = state.versionCode,
              onValueChange = viewModel::updateVersionCode,
              label = { Text(stringResource(R.string.label_version_code)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
              modifier = Modifier.weight(0.75f).testTag("version_code_input"),
            )
          }
        }
      }

      // Section 2: Web URL or HTML/ZIP File
      item {
        ForgeSectionCard(
          title = stringResource(R.string.section_source),
          icon = Icons.Filled.Language,
          stepBadge = "2",
        ) {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = state.sourceType == SourceType.URL,
              onClick = { viewModel.updateSourceType(SourceType.URL) },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
              icon = {
                Icon(
                  Icons.Filled.Language,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              modifier = Modifier.testTag("source_mode_url"),
            ) {
              Text(stringResource(R.string.source_mode_url), maxLines = 1)
            }
            SegmentedButton(
              selected = state.sourceType == SourceType.LOCAL_FILE,
              onClick = { viewModel.updateSourceType(SourceType.LOCAL_FILE) },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
              icon = {
                Icon(
                  Icons.Filled.FolderOpen,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              modifier = Modifier.testTag("source_mode_file"),
            ) {
              Text(stringResource(R.string.source_mode_file), maxLines = 1)
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          if (state.sourceType == SourceType.URL) {
            OutlinedTextField(
              value = state.webUrl,
              onValueChange = viewModel::updateWebUrl,
              label = { Text(stringResource(R.string.label_web_url)) },
              placeholder = { Text(stringResource(R.string.hint_web_url)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
              isError = state.sourceError != null,
              supportingText = state.sourceError?.let { err -> { Text(err) } },
              trailingIcon = {
                IconButton(
                  onClick = viewModel::openWebPreview,
                  modifier = Modifier.testTag("preview_web_button"),
                ) {
                  Icon(
                    Icons.Filled.OpenInBrowser,
                    contentDescription = stringResource(R.string.action_preview_web),
                    tint = MaterialTheme.colorScheme.secondary,
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("web_url_input"),
            )
          } else {
            val fileBytes = state.localFileBytes
            val fileName = state.localFileName
            if (fileBytes != null && fileName != null) {
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                  )
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = fileName,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${ValidationUtils.formatFileSize(fileBytes.size.toLong())} • Siap dikemas ke assets/web/",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(onClick = viewModel::openWebPreview) {
                    Icon(
                      Icons.Filled.OpenInBrowser,
                      contentDescription = stringResource(R.string.action_preview_web),
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(onClick = viewModel::clearLocalFile) {
                    Icon(Icons.Filled.Close, contentDescription = "Hapus file")
                  }
                }
              }
              Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
              onClick = {
                filePickerLauncher.launch(
                  arrayOf("text/html", "application/zip", "application/x-zip-compressed", "*/*")
                )
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_file_button"),
            ) {
              Icon(Icons.Filled.FolderOpen, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(
                if (fileBytes == null) stringResource(R.string.action_pick_file)
                else stringResource(R.string.action_change_file)
              )
            }

            if (state.sourceError != null) {
              Spacer(Modifier.height(6.dp))
              Text(
                text = state.sourceError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }

      // Section 3: Android Permissions (Required >= 1)
      item {
        ForgeSectionCard(
          title = stringResource(R.string.section_permissions),
          icon = Icons.Filled.Security,
          stepBadge = "3",
          trailingBadge = "${state.selectedPermissions.size} dipilih",
        ) {
          if (state.permissionError != null) {
            Surface(
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Filled.WarningAmber,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = state.permissionError!!,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }

          if (state.internetMissingWarning) {
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Filled.WarningAmber,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "Mode URL Web membutuhkan android.permission.INTERNET agar halaman dapat dimuat.",
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }

          // Category filter chips
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = state.selectedPermissionFilter == null,
              onClick = { viewModel.updatePermissionFilter(null) },
              label = { Text("Semua") },
            )
            PermissionCategory.entries.forEach { category ->
              FilterChip(
                selected = state.selectedPermissionFilter == category,
                onClick = { viewModel.updatePermissionFilter(category) },
                label = { Text(category.label) },
              )
            }
          }

          Spacer(Modifier.height(10.dp))

          val visiblePermissions =
            PresetPermissions.all.filter {
              state.selectedPermissionFilter == null || it.category == state.selectedPermissionFilter
            }

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visiblePermissions.forEach { option ->
              val checked = option.permission in state.selectedPermissions
              PermissionToggleRow(
                shortName = option.shortName,
                fullPermission = option.permission,
                title = option.title,
                description = option.description,
                isDangerous = option.isDangerous,
                checked = checked,
                onToggle = { viewModel.togglePermission(option.permission) },
              )
            }

            state.customPermissions.forEach { customPerm ->
              val checked = customPerm in state.selectedPermissions
              PermissionToggleRow(
                shortName = customPerm.substringAfterLast('.'),
                fullPermission = customPerm,
                title = "Permission Kustom",
                description = "Izin tambahan dari pengguna.",
                isDangerous = false,
                checked = checked,
                onToggle = { viewModel.togglePermission(customPerm) },
              )
            }
          }

          Spacer(Modifier.height(12.dp))

          // Custom permission adder
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = state.customPermissionInput,
              onValueChange = viewModel::updateCustomPermissionInput,
              label = { Text("Tambah Custom Permission") },
              placeholder = { Text("android.permission.BLUETOOTH") },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
              isError = state.customPermissionError != null,
              supportingText = state.customPermissionError?.let { err -> { Text(err) } },
              modifier = Modifier.weight(1f).testTag("custom_permission_input"),
            )
            Button(
              onClick = viewModel::addCustomPermission,
              modifier = Modifier.padding(top = 6.dp).height(52.dp).testTag("add_permission_button"),
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(Icons.Filled.Add, contentDescription = "Tambah")
            }
          }
        }
      }

      // Section 4: Advanced WebView Options & Live Manifest XML
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .clickable { showAdvanced = !showAdvanced }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Spacer(Modifier.width(10.dp))
              Text(
                text = stringResource(R.string.section_webview),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
              )
              Icon(
                if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = "Buka opsi lanjutan",
              )
            }

            AnimatedVisibility(visible = showAdvanced) {
              Column(modifier = Modifier.padding(top = 12.dp)) {
                WebViewToggleItem(
                  label = "Aktifkan JavaScript",
                  subtitle = "Diperlukan oleh hampir semua situs web modern.",
                  checked = state.jsEnabled,
                  onCheckedChange = viewModel::toggleJs,
                )
                WebViewToggleItem(
                  label = "DOM Storage & LocalStorage",
                  subtitle = "Menyimpan sesi login dan cache web di perangkat.",
                  checked = state.domStorageEnabled,
                  onCheckedChange = viewModel::toggleDomStorage,
                )
                WebViewToggleItem(
                  label = "Mode Layar Penuh (Fullscreen)",
                  subtitle = "Menyembunyikan bilah judul sistem saat aplikasi dibuka.",
                  checked = state.fullscreen,
                  onCheckedChange = viewModel::toggleFullscreen,
                )
                WebViewToggleItem(
                  label = "Izinkan Zoom (Pinch-to-Zoom)",
                  subtitle = "Mengaktifkan kontrol perbesaran halaman web.",
                  checked = state.zoomEnabled,
                  onCheckedChange = viewModel::toggleZoom,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                  "Orientasi Layar",
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ScreenOrientation.entries.forEach { orient ->
                    FilterChip(
                      selected = state.orientation == orient,
                      onClick = { viewModel.updateOrientation(orient) },
                      label = { Text(orient.label) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                    )
                  }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(MaterialTheme.shapes.small)
                      .clickable { showManifestPreview = !showManifestPreview }
                      .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Filled.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Text(
                    "Pratinjau AndroidManifest.xml",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    if (showManifestPreview) "Tutup" else "Lihat XML",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                AnimatedVisibility(visible = showManifestPreview) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFF0A0E17),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  ) {
                    Text(
                      text = state.manifestPreviewXml(),
                      style =
                        MaterialTheme.typography.labelSmall.copy(
                          color = Color(0xFF34D399),
                          fontFamily = JetBrainsMonoFontFamily,
                        ),
                      modifier =
                        Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Primary Build CTA
      item {
        Button(
          onClick = viewModel::buildApk,
          modifier =
            Modifier.fillMaxWidth()
              .height(58.dp)
              .testTag("build_apk_button"),
          shape = MaterialTheme.shapes.medium,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Icon(Icons.Filled.Build, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.action_build_apk),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}

@Composable
private fun HeroForgeCard(state: Web2ApkUiState) {
  val readyApp = ValidationUtils.validateAppName(state.appName) == null
  val readyPkg = ValidationUtils.validatePackageName(state.packageName) == null
  val readySource =
    when (state.sourceType) {
      SourceType.URL -> ValidationUtils.validateWebUrl(state.webUrl) == null
      SourceType.LOCAL_FILE -> state.localFileBytes != null
    }
  val readyPerms = state.selectedPermissions.isNotEmpty()
  val readyCount = listOf(readyApp, readyPkg, readySource, readyPerms).count { it }

  Card(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_forge_banner),
        contentDescription = stringResource(R.string.cd_banner_art),
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color(0xD90B101B),
                    Color(0xEE0B101B),
                  )
              )
            )
      )
      Column(modifier = Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
          ) {
            Icon(
              Icons.Filled.Settings,
              contentDescription = null,
              tint = Color(0xFF34D399),
              modifier = Modifier.padding(8.dp).size(20.dp),
            )
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Web2APK Forge Engine",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
            )
            Text(
              text = "Kompilasi AXML, DEX & Penandatanganan v1+v2 Instan",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFCBD5E1),
            )
          }
        }

        Spacer(Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ReadinessChip("Nama", readyApp, Modifier.weight(1f))
          ReadinessChip("Package", readyPkg, Modifier.weight(1f))
          ReadinessChip("Sumber", readySource, Modifier.weight(1f))
          ReadinessChip("Izin (${state.selectedPermissions.size})", readyPerms, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Text(
          text =
            if (readyCount == 4) "Semua kolom wajib lengkap • Siap dikemas ke APK"
            else "$readyCount/4 syarat wajib terpenuhi",
          style = MaterialTheme.typography.labelSmall,
          color = if (readyCount == 4) Color(0xFF34D399) else Color(0xFF94A3B8),
        )
      }
    }
  }
}

@Composable
private fun ReadinessChip(label: String, ready: Boolean, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = if (ready) Color(0x3310B981) else Color(0x22FFFFFF),
    border =
      BorderStroke(
        1.dp,
        if (ready) Color(0x9934D399) else Color(0x3394A3B8),
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = if (ready) Color(0xFF34D399) else Color(0xFF64748B),
        modifier = Modifier.size(13.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (ready) Color.White else Color(0xFF94A3B8),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ForgeSectionCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  stepBadge: String,
  trailingBadge: String? = null,
  content: @Composable () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(28.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stepBadge,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
        if (trailingBadge != null) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp),
          ) {
            Text(
              text = trailingBadge,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
      content()
    }
  }
}

@Composable
private fun AppIconRow(
  appName: String,
  customIconUri: String?,
  selectedPresetId: String,
  onPickImage: () -> Unit,
  onSelectPreset: (String) -> Unit,
) {
  val activePreset =
    IconColorPresets.all.firstOrNull { it.id == selectedPresetId } ?: IconColorPresets.all.first()
  val initials =
    appName
      .trim()
      .split(Regex("\\s+"))
      .filter { it.isNotEmpty() }
      .take(2)
      .joinToString("") { it.first().uppercaseChar().toString() }
      .ifEmpty { "W2" }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      modifier =
        Modifier.size(64.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(activePreset.backgroundHex))
          .border(2.dp, Color(activePreset.accentHex), RoundedCornerShape(16.dp))
          .clickable(onClick = onPickImage)
          .testTag("pick_icon_box"),
      contentAlignment = Alignment.Center,
    ) {
      if (customIconUri != null) {
        AsyncImage(
          model = customIconUri,
          contentDescription = stringResource(R.string.cd_app_icon),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text(
          text = initials,
          style = MaterialTheme.typography.headlineMedium,
          color = Color(activePreset.accentHex),
        )
      }
    }

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Ikon APK", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPickImage, modifier = Modifier.testTag("pick_icon_button")) {
          Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Pilih Gambar")
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconColorPresets.all.forEach { preset ->
          val isSelected = customIconUri == null && preset.id == selectedPresetId
          Box(
            modifier =
              Modifier.size(28.dp)
                .clip(CircleShape)
                .background(Color(preset.backgroundHex))
                .border(
                  width = if (isSelected) 2.5.dp else 1.dp,
                  color =
                    if (isSelected) Color(preset.accentHex)
                    else MaterialTheme.colorScheme.outline,
                  shape = CircleShape,
                )
                .clickable { onSelectPreset(preset.id) }
          ) {
            Box(
              modifier =
                Modifier.size(10.dp)
                  .align(Alignment.Center)
                  .clip(CircleShape)
                  .background(Color(preset.accentHex))
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionToggleRow(
  shortName: String,
  fullPermission: String,
  title: String,
  description: String,
  isDangerous: Boolean,
  checked: Boolean,
  onToggle: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color =
      if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    border =
      BorderStroke(
        1.dp,
        if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable(onClick = onToggle)
        .testTag("perm_$shortName"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text = title, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.width(8.dp))
          Surface(
            color =
              if (isDangerous) MaterialTheme.colorScheme.tertiaryContainer
              else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = if (isDangerous) "Runtime" else "Normal",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isDangerous) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = fullPermission,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(8.dp))
      Switch(checked = checked, onCheckedChange = { onToggle() })
    }
  }
}

@Composable
private fun WebViewToggleItem(
  label: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.titleMedium)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
