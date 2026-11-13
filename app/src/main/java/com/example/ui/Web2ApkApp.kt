package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import java.io.File

enum class Web2ApkTab {
  BUILDER,
  HISTORY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Web2ApkApp(viewModel: Web2ApkViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val builtApks by viewModel.builtApks.collectAsStateWithLifecycle()
  var currentTab by rememberSaveable { mutableStateOf(Web2ApkTab.BUILDER) }

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var pendingSavePath by remember { mutableStateOf<String?>(null) }

  val saveApkLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
      val srcPath = pendingSavePath
      pendingSavePath = null
      if (uri != null && srcPath != null) {
        viewModel.exportApkToUri(srcPath, uri)
      }
    }

  LaunchedEffect(uiState.userMessage) {
    val msg = uiState.userMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearUserMessage()
    }
  }

  fun launchSave(apkPath: String, fileName: String) {
    pendingSavePath = apkPath
    saveApkLauncher.launch(fileName)
  }

  fun launchShare(apkPath: String, appName: String) {
    shareApkFile(context, apkPath, appName)
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
              )
              Spacer(Modifier.width(8.dp))
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
              ) {
                Text(
                  text = "APK Builder",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            ),
        )
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(
              selected = currentTab == Web2ApkTab.BUILDER,
              onClick = { currentTab = Web2ApkTab.BUILDER },
              icon = {
                Icon(
                  if (currentTab == Web2ApkTab.BUILDER) Icons.Filled.Build
                  else Icons.Outlined.Build,
                  contentDescription = null,
                )
              },
              label = { Text(stringResource(R.string.tab_builder)) },
              modifier = Modifier.testTag("nav_builder"),
            )
            NavigationBarItem(
              selected = currentTab == Web2ApkTab.HISTORY,
              onClick = { currentTab = Web2ApkTab.HISTORY },
              icon = {
                BadgedBox(
                  badge = {
                    if (builtApks.isNotEmpty()) {
                      Badge { Text("${builtApks.size}") }
                    }
                  }
                ) {
                  Icon(
                    if (currentTab == Web2ApkTab.HISTORY) Icons.Filled.History
                    else Icons.Outlined.History,
                    contentDescription = null,
                  )
                }
              },
              label = { Text(stringResource(R.string.tab_history)) },
              modifier = Modifier.testTag("nav_history"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationRailItem(
              selected = currentTab == Web2ApkTab.BUILDER,
              onClick = { currentTab = Web2ApkTab.BUILDER },
              icon = {
                Icon(
                  if (currentTab == Web2ApkTab.BUILDER) Icons.Filled.Build
                  else Icons.Outlined.Build,
                  contentDescription = null,
                )
              },
              label = { Text(stringResource(R.string.tab_builder)) },
            )
            NavigationRailItem(
              selected = currentTab == Web2ApkTab.HISTORY,
              onClick = { currentTab = Web2ApkTab.HISTORY },
              icon = {
                BadgedBox(
                  badge = {
                    if (builtApks.isNotEmpty()) {
                      Badge { Text("${builtApks.size}") }
                    }
                  }
                ) {
                  Icon(
                    if (currentTab == Web2ApkTab.HISTORY) Icons.Filled.History
                    else Icons.Outlined.History,
                    contentDescription = null,
                  )
                }
              },
              label = { Text(stringResource(R.string.tab_history)) },
            )
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
          when (currentTab) {
            Web2ApkTab.BUILDER ->
              BuilderScreen(state = uiState, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            Web2ApkTab.HISTORY ->
              HistoryScreen(
                apks = builtApks,
                searchQuery = uiState.historySearchQuery,
                onSearchChange = viewModel::updateHistorySearch,
                onSaveApk = { launchSave(it.apkFilePath, it.apkFileName) },
                onShareApk = { launchShare(it.apkFilePath, it.appName) },
                onInspectApk = viewModel::inspectHistoryItem,
                onLoadToBuilder = { entity ->
                  viewModel.loadFromHistory(entity)
                  currentTab = Web2ApkTab.BUILDER
                },
                onDeleteApk = viewModel::deleteHistoryItem,
                onGoToBuilder = { currentTab = Web2ApkTab.BUILDER },
                modifier = Modifier.fillMaxSize(),
              )
          }
        }
      }
    }
  }

  // Modals
  BuildStatusModal(
    buildState = uiState.buildState,
    onSaveApk = { res -> launchSave(res.apkFilePath, res.fileName) },
    onShareApk = { res -> launchShare(res.apkFilePath, res.appName) },
    onInspectApk = { res ->
      val matched = builtApks.firstOrNull { it.id == res.entityId }
      if (matched != null) {
        viewModel.dismissBuildState()
        viewModel.inspectHistoryItem(matched)
      }
    },
    onDismiss = viewModel::dismissBuildState,
  )

  uiState.inspectorState?.let { inspector ->
    ApkInspectorDialog(inspectorState = inspector, onDismiss = viewModel::dismissInspector)
  }

  if (uiState.showWebPreview) {
    WebPreviewDialog(state = uiState, onDismiss = viewModel::closeWebPreview)
  }
}

private fun shareApkFile(context: Context, apkPath: String, appName: String) {
  val file = File(apkPath)
  if (!file.exists()) return
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.android.package-archive"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "$appName.apk")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  context.startActivity(Intent.createChooser(shareIntent, "Bagikan File APK"))
}
