package com.example

import com.example.apkbuilder.ApkSigner
import com.example.apkbuilder.AxmlManifestWriter
import com.example.apkbuilder.MinimalWebViewDexWriter
import com.example.apkbuilder.ResourcesArscWriter
import com.example.model.ScreenOrientation
import com.example.model.SourceType
import com.example.model.ValidationUtils
import com.example.model.Web2ApkBuildRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderUnitTest {

  @Test
  fun `validation utils validate package name and urls accurately`() {
    assertNull(ValidationUtils.validatePackageName("com.tokoku.app"))
    assertNotNull(ValidationUtils.validatePackageName("tokoku"))
    assertNotNull(ValidationUtils.validatePackageName("com.class.app"))
    assertNull(ValidationUtils.validateWebUrl("https://example.com"))
    assertNotNull(ValidationUtils.validateWebUrl("ftp://example.com"))
    assertEquals("com.web2apk.tokoku", ValidationUtils.suggestPackageName("Toko Ku!"))
  }

  @Test
  fun `dex writer generates valid dex035 header sha1 and adler32 checksum`() {
    val dex =
      MinimalWebViewDexWriter.buildWebViewDex(
        targetUrl = "https://example.com",
        jsEnabled = true,
        domStorageEnabled = true,
        fullscreen = false,
        zoomEnabled = true,
      )
    val magic = String(dex.copyOfRange(0, 8), Charsets.US_ASCII)
    assertEquals("dex\n035\u0000", magic)

    val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
    val storedChecksum = buf.getInt(8)
    val storedSha1 = dex.copyOfRange(12, 32)
    val fileSize = buf.getInt(32)
    assertEquals(dex.size, fileSize)

    val computedSha1 = MessageDigest.getInstance("SHA-1").digest(dex.copyOfRange(32, dex.size))
    assertArrayEquals(computedSha1, storedSha1)

    val adler = Adler32()
    adler.update(dex, 12, dex.size - 12)
    assertEquals(adler.value.toInt(), storedChecksum)
  }

  @Test
  fun `axml and arsc writers emit valid binary headers`() {
    val req =
      Web2ApkBuildRequest(
        appName = "TestApp",
        packageName = "com.test.app",
        versionName = "1.0",
        versionCode = 1,
        sourceType = SourceType.URL,
        webUrl = "https://example.com",
        localFileUri = null,
        localFileName = null,
        localFileBytes = null,
        permissions = listOf("android.permission.INTERNET"),
        jsEnabled = true,
        domStorageEnabled = true,
        fullscreen = false,
        zoomEnabled = false,
        orientation = ScreenOrientation.AUTO,
        customIconBytes = null,
        iconPresetId = "emerald",
      )
    val axml = AxmlManifestWriter.compileBinaryManifest(req, "https://example.com")
    val axmlBuf = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(0x0003.toShort(), axmlBuf.getShort(0))
    assertEquals(axml.size, axmlBuf.getInt(4))

    val arsc = ResourcesArscWriter.compileResources("com.test.app", "TestApp")
    val arscBuf = ByteBuffer.wrap(arsc).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(0x0002.toShort(), arscBuf.getShort(0))
    assertEquals(arsc.size, arscBuf.getInt(4))
  }

  @Test
  fun `apk signer generates valid x509 certificate and pkcs7 block`() {
    val creds = ApkSigner.generateSigningCredentials("TestApp")
    assertTrue(creds.certificate.subjectX500Principal.name.contains("TestApp"))
    val mf = ApkSigner.buildJarV1Manifest(mapOf("AndroidManifest.xml" to byteArrayOf(1, 2, 3)))
    val sf = ApkSigner.buildJarV1SignatureFile(mf, mapOf("AndroidManifest.xml" to byteArrayOf(1, 2, 3)))
    val rsa = ApkSigner.buildPkcs7SignatureBlock(sf, creds)
    assertTrue(rsa.isNotEmpty())
  }
}
