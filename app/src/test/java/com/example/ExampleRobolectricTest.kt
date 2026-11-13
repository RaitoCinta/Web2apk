package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.apkbuilder.ApkBuilderEngine
import com.example.data.AppDatabase
import com.example.data.ApkRepository
import com.example.model.ScreenOrientation
import com.example.model.SourceType
import com.example.model.Web2ApkBuildRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Web2APK`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Web2APK", appName)
  }

  @Test
  fun `buildApk packages valid signed apk from url and persists to Room`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val repo = ApkRepository(db.builtApkDao())
    val engine = ApkBuilderEngine(context, repo)

    val request =
      Web2ApkBuildRequest(
        appName = "TokoKu",
        packageName = "com.web2apk.tokoku",
        versionName = "1.2.0",
        versionCode = 3,
        sourceType = SourceType.URL,
        webUrl = "https://tokoku.example.com",
        localFileUri = null,
        localFileName = null,
        localFileBytes = null,
        permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
        jsEnabled = true,
        domStorageEnabled = true,
        fullscreen = false,
        zoomEnabled = false,
        orientation = ScreenOrientation.PORTRAIT,
        customIconBytes = null,
        iconPresetId = "emerald",
      )

    val result = engine.buildApk(request) {}
    val apkFile = File(result.apkFilePath)
    assertTrue(apkFile.exists())
    assertTrue(result.fileSizeBytes > 1000L)

    // Verify ZIP entries
    ZipFile(apkFile).use { zip ->
      assertTrue(zip.getEntry("AndroidManifest.xml") != null)
      assertTrue(zip.getEntry("classes.dex") != null)
      assertTrue(zip.getEntry("resources.arsc") != null)
      assertTrue(zip.getEntry("res/mipmap-xxxhdpi-v4/ic_launcher.png") != null)
      assertTrue(zip.getEntry("assets/web2apk_config.json") != null)
      assertTrue(zip.getEntry("META-INF/MANIFEST.MF") != null)
      assertTrue(zip.getEntry("META-INF/CERT.SF") != null)
      assertTrue(zip.getEntry("META-INF/CERT.RSA") != null)
    }

    // Verify APK Sig Block 42 magic exists in file
    val rawBytes = apkFile.readBytes()
    val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    assertTrue(containsSubarray(rawBytes, magic))

    // Verify persisted in Room
    val saved = repo.allBuiltApks.first()
    assertEquals(1, saved.size)
    assertEquals("com.web2apk.tokoku", saved.first().packageName)
    db.close()
  }

  @Test
  fun `buildApk packages zip web bundle into assets web`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val repo = ApkRepository(db.builtApkDao())
    val engine = ApkBuilderEngine(context, repo)

    val zipOut = ByteArrayOutputStream()
    ZipOutputStream(zipOut).use { zos ->
      zos.putNextEntry(ZipEntry("index.html"))
      zos.write("<html><body><h1>Halo Web2APK</h1></body></html>".toByteArray())
      zos.closeEntry()
      zos.putNextEntry(ZipEntry("style.css"))
      zos.write("body { color: #10B981; }".toByteArray())
      zos.closeEntry()
    }

    val request =
      Web2ApkBuildRequest(
        appName = "Offline Portal",
        packageName = "com.web2apk.offline",
        versionName = "1.0.0",
        versionCode = 1,
        sourceType = SourceType.LOCAL_FILE,
        webUrl = "",
        localFileUri = "content://test/site.zip",
        localFileName = "site.zip",
        localFileBytes = zipOut.toByteArray(),
        permissions = listOf("android.permission.ACCESS_NETWORK_STATE"),
        jsEnabled = true,
        domStorageEnabled = true,
        fullscreen = true,
        zoomEnabled = true,
        orientation = ScreenOrientation.AUTO,
        customIconBytes = null,
        iconPresetId = "cyan",
      )

    val result = engine.buildApk(request) {}
    val apkFile = File(result.apkFilePath)
    ZipFile(apkFile).use { zip ->
      assertTrue(zip.getEntry("assets/web/index.html") != null)
      assertTrue(zip.getEntry("assets/web/style.css") != null)
    }
    db.close()
  }

  private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
    outer@ for (i in 0..(haystack.size - needle.size)) {
      for (j in needle.indices) {
        if (haystack[i + j] != needle[j]) continue@outer
      }
      return true
    }
    return false
  }
}
