package com.example.apkbuilder

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure-Kotlin APK Signer implementing:
 * 1. Self-signed RSA-2048 X.509 v3 certificate generation
 * 2. JAR Signature v1 (`META-INF/MANIFEST.MF`, `META-INF/CERT.SF`, `META-INF/CERT.RSA`)
 * 3. APK Signature Scheme v2 (`APK Sig Block 42`)
 */
object ApkSigner {
  private const val APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L // "APK Sig "
  private const val APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L // "Block 42"
  private const val APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a
  private const val SIG_ALG_RSA_PKCS1_V1_5_SHA256 = 0x0103
  private const val CHUNK_SIZE = 1024 * 1024

  data class SigningCredentials(
    val keyPair: KeyPair,
    val certificate: X509Certificate,
    val certDer: ByteArray,
  )

  fun generateSigningCredentials(commonName: String): SigningCredentials {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048, SecureRandom())
    val keyPair = kpg.generateKeyPair()
    val certDer = buildSelfSignedX509Der(keyPair, commonName)
    val cert =
      CertificateFactory.getInstance("X.509")
        .generateCertificate(certDer.inputStream()) as X509Certificate
    return SigningCredentials(keyPair, cert, certDer)
  }

  fun buildJarV1Manifest(entries: Map<String, ByteArray>): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val sb = StringBuilder()
    sb.append("Manifest-Version: 1.0\r\n")
    sb.append("Created-By: Web2APK Studio\r\n\r\n")
    entries.toSortedMap().forEach { (name, bytes) ->
      val b64 = androidBase64(digest.digest(bytes))
      sb.append("Name: $name\r\n")
      sb.append("SHA-256-Digest: $b64\r\n\r\n")
    }
    return sb.toString().toByteArray(Charsets.UTF_8)
  }

  fun buildJarV1SignatureFile(manifestBytes: ByteArray, entries: Map<String, ByteArray>): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val manifestB64 = androidBase64(digest.digest(manifestBytes))
    val sb = StringBuilder()
    sb.append("Signature-Version: 1.0\r\n")
    sb.append("Created-By: Web2APK Studio\r\n")
    sb.append("SHA-256-Digest-Manifest: $manifestB64\r\n\r\n")

    entries.toSortedMap().forEach { (name, bytes) ->
      val entrySection = "Name: $name\r\nSHA-256-Digest: ${androidBase64(digest.digest(bytes))}\r\n\r\n"
      val sectionDigest = androidBase64(digest.digest(entrySection.toByteArray(Charsets.UTF_8)))
      sb.append("Name: $name\r\n")
      sb.append("SHA-256-Digest: $sectionDigest\r\n\r\n")
    }
    return sb.toString().toByteArray(Charsets.UTF_8)
  }

  fun buildPkcs7SignatureBlock(sfBytes: ByteArray, credentials: SigningCredentials): ByteArray {
    val sig = Signature.getInstance("SHA256withRSA")
    sig.initSign(credentials.keyPair.private)
    sig.update(sfBytes)
    val signatureBytes = sig.sign()

    // OIDs
    val oidPkcs7SignedData = derOid(1, 2, 840, 113549, 1, 7, 2)
    val oidPkcs7Data = derOid(1, 2, 840, 113549, 1, 7, 1)
    val oidSha256 = derOid(2, 16, 840, 1, 101, 3, 4, 2, 1)
    val oidRsaEncryption = derOid(1, 2, 840, 113549, 1, 1, 1)

    val digestAlgId = derSeq(oidSha256 + derNull())
    val digestAlgsSet = derSet(digestAlgId)
    val contentInfo = derSeq(oidPkcs7Data)
    val certsTagged = derTag(0xA0, credentials.certDer)

    // IssuerAndSerialNumber from X.509 cert
    val issuerDer = credentials.certificate.issuerX500Principal.encoded
    val serialDer = derInteger(credentials.certificate.serialNumber)
    val issuerAndSerial = derSeq(issuerDer + serialDer)

    val signerInfo =
      derSeq(
        derInteger(BigInteger.ONE) +
          issuerAndSerial +
          digestAlgId +
          derSeq(oidRsaEncryption + derNull()) +
          derOctetString(signatureBytes)
      )
    val signerInfosSet = derSet(signerInfo)

    val signedData =
      derSeq(
        derInteger(BigInteger.ONE) +
          digestAlgsSet +
          contentInfo +
          certsTagged +
          signerInfosSet
      )
    return derSeq(oidPkcs7SignedData + derTag(0xA0, signedData))
  }

  /**
   * Inserts an APK Signature Scheme v2 Signing Block into an existing ZIP/APK file.
   */
  fun applyApkSignatureV2(apkFile: java.io.File, credentials: SigningCredentials) {
    RandomAccessFile(apkFile, "rw").use { raf ->
      val fileLen = raf.length()
      val eocdOffset = findEocdOffset(raf, fileLen)
      raf.seek(eocdOffset + 12)
      val cdSize = Integer.toUnsignedLong(readIntLE(raf))
      val cdOffset = Integer.toUnsignedLong(readIntLE(raf))

      // Read Central Directory and EOCD
      val cdBytes = ByteArray(cdSize.toInt())
      raf.seek(cdOffset)
      raf.readFully(cdBytes)

      val eocdLen = (fileLen - eocdOffset).toInt()
      val eocdBytes = ByteArray(eocdLen)
      raf.seek(eocdOffset)
      raf.readFully(eocdBytes)

      // Compute APK Signature Scheme v2 content digest over Sections 1, 3, 4
      val contentDigest = computeV2ContentDigest(raf, cdOffset, cdBytes, eocdBytes)

      // Build SignerData
      val digests = lengthPrefixed(lengthPrefixed(intLeBytes(SIG_ALG_RSA_PKCS1_V1_5_SHA256) + lengthPrefixed(contentDigest)))
      val certs = lengthPrefixed(lengthPrefixed(credentials.certDer))
      val attrs = lengthPrefixed(ByteArray(0))
      val signedData = digests + certs + attrs

      val signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(credentials.keyPair.private)
      signature.update(signedData)
      val sigBytes = signature.sign()

      val signatures = lengthPrefixed(lengthPrefixed(intLeBytes(SIG_ALG_RSA_PKCS1_V1_5_SHA256) + lengthPrefixed(sigBytes)))
      val pubKeyDer = credentials.keyPair.public.encoded
      val signer = lengthPrefixed(signedData) + signatures + lengthPrefixed(pubKeyDer)
      val signers = lengthPrefixed(lengthPrefixed(signer))

      val v2BlockValue = signers
      // Pair: uint64 size + uint32 id + value
      val pairLen = 4 + v2BlockValue.size
      val blockContentSize = 8 + pairLen
      // Total block size = 8 (size_of_block) + blockContentSize + 8 (size_of_block) + 16 (magic)
      val totalBlockSize = 8L + blockContentSize.toLong() + 8L + 16L
      val sizeOfBlockField = totalBlockSize - 8L

      val sigBlock = ByteBuffer.allocate(totalBlockSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
      sigBlock.putLong(sizeOfBlockField)
      sigBlock.putLong(pairLen.toLong())
      sigBlock.putInt(APK_SIGNATURE_SCHEME_V2_BLOCK_ID)
      sigBlock.put(v2BlockValue)
      sigBlock.putLong(sizeOfBlockField)
      sigBlock.putLong(APK_SIG_BLOCK_MAGIC_LO)
      sigBlock.putLong(APK_SIG_BLOCK_MAGIC_HI)

      // Patch EOCD CD offset
      val newCdOffset = (cdOffset + totalBlockSize).toInt()
      eocdBytes[16] = (newCdOffset and 0xFF).toByte()
      eocdBytes[17] = ((newCdOffset ushr 8) and 0xFF).toByte()
      eocdBytes[18] = ((newCdOffset ushr 16) and 0xFF).toByte()
      eocdBytes[19] = ((newCdOffset ushr 24) and 0xFF).toByte()

      // Rewrite from cdOffset onwards: [APK Sig Block][CD][EOCD]
      raf.seek(cdOffset)
      raf.write(sigBlock.array())
      raf.write(cdBytes)
      raf.write(eocdBytes)
      raf.setLength(cdOffset + totalBlockSize + cdBytes.size + eocdBytes.size)
    }
  }

  private fun computeV2ContentDigest(
    raf: RandomAccessFile,
    cdOffset: Long,
    cdBytes: ByteArray,
    eocdBytes: ByteArray,
  ): ByteArray {
    // Patch a copy of EOCD with cdOffset for the digest (section 4) — v2 spec expects original cdOffset before sig block insertion
    val chunkDigests = mutableListOf<ByteArray>()
    val md = MessageDigest.getInstance("SHA-256")

    fun hashSectionChunks(readChunk: (Long, ByteArray) -> Int, totalLen: Long) {
      var offset = 0L
      val buf = ByteArray(CHUNK_SIZE)
      while (offset < totalLen) {
        val len = minOf(CHUNK_SIZE.toLong(), totalLen - offset).toInt()
        readChunk(offset, buf.copyOf(len)) // fallback below
        offset += len
      }
    }

    // Section 1: ZIP entries [0..cdOffset)
    run {
      var pos = 0L
      val buf = ByteArray(CHUNK_SIZE)
      raf.seek(0)
      while (pos < cdOffset) {
        val len = minOf(CHUNK_SIZE.toLong(), cdOffset - pos).toInt()
        raf.readFully(buf, 0, len)
        md.reset()
        md.update(0xA5.toByte())
        md.update(intLeBytes(len))
        md.update(buf, 0, len)
        chunkDigests += md.digest()
        pos += len
      }
    }
    // Section 3: Central Directory
    run {
      var pos = 0
      while (pos < cdBytes.size) {
        val len = minOf(CHUNK_SIZE, cdBytes.size - pos)
        md.reset()
        md.update(0xA5.toByte())
        md.update(intLeBytes(len))
        md.update(cdBytes, pos, len)
        chunkDigests += md.digest()
        pos += len
      }
    }
    // Section 4: EOCD
    run {
      md.reset()
      md.update(0xA5.toByte())
      md.update(intLeBytes(eocdBytes.size))
      md.update(eocdBytes)
      chunkDigests += md.digest()
    }

    md.reset()
    md.update(0x5A.toByte())
    md.update(intLeBytes(chunkDigests.size))
    chunkDigests.forEach { md.update(it) }
    return md.digest()
  }

  private fun findEocdOffset(raf: RandomAccessFile, fileLen: Long): Long {
    val searchStart = maxOf(0L, fileLen - 65557L)
    val searchLen = (fileLen - searchStart).toInt()
    val buf = ByteArray(searchLen)
    raf.seek(searchStart)
    raf.readFully(buf)
    for (i in searchLen - 22 downTo 0) {
      if (
        buf[i] == 0x50.toByte() &&
          buf[i + 1] == 0x4b.toByte() &&
          buf[i + 2] == 0x05.toByte() &&
          buf[i + 3] == 0x06.toByte()
      ) {
        return searchStart + i
      }
    }
    throw IllegalStateException("ZIP EOCD not found")
  }

  private fun readIntLE(raf: RandomAccessFile): Int {
    val b0 = raf.read()
    val b1 = raf.read()
    val b2 = raf.read()
    val b3 = raf.read()
    return (b0 and 0xFF) or
      ((b1 and 0xFF) shl 8) or
      ((b2 and 0xFF) shl 16) or
      ((b3 and 0xFF) shl 24)
  }

  private fun buildSelfSignedX509Der(keyPair: KeyPair, commonName: String): ByteArray {
    val serial = BigInteger(64, SecureRandom()).abs().max(BigInteger.ONE)
    val oidSha256WithRsa = derOid(1, 2, 840, 113549, 1, 1, 11)
    val algId = derSeq(oidSha256WithRsa + derNull())

    val oidCommonName = derOid(2, 5, 4, 3)
    val oidOrg = derOid(2, 5, 4, 10)
    val rdnCn = derSet(derSeq(oidCommonName + derUtf8String(commonName.take(48).ifEmpty { "Web2APK" })))
    val rdnOrg = derSet(derSeq(oidOrg + derUtf8String("Web2APK Studio")))
    val nameDer = derSeq(rdnCn + rdnOrg)

    val now = Date()
    val later = Date(now.time + 1000L * 60 * 60 * 24 * 365 * 25)
    val validity = derSeq(derUtcTime(now) + derUtcTime(later))
    val spki = keyPair.public.encoded

    val tbsCert =
      derSeq(
        derTag(0xA0, derInteger(BigInteger.valueOf(2))) + // v3
          derInteger(serial) +
          algId +
          nameDer +
          validity +
          nameDer +
          spki
      )

    val sig = Signature.getInstance("SHA256withRSA")
    sig.initSign(keyPair.private)
    sig.update(tbsCert)
    val sigBytes = sig.sign()

    return derSeq(tbsCert + algId + derBitString(sigBytes))
  }

  private fun lengthPrefixed(data: ByteArray): ByteArray {
    val buf = ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(data.size)
    buf.put(data)
    return buf.array()
  }

  private fun intLeBytes(value: Int): ByteArray {
    return byteArrayOf(
      (value and 0xFF).toByte(),
      ((value ushr 8) and 0xFF).toByte(),
      ((value ushr 16) and 0xFF).toByte(),
      ((value ushr 24) and 0xFF).toByte(),
    )
  }

  private fun androidBase64(bytes: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
      val b0 = bytes[i].toInt() and 0xFF
      val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
      val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
      sb.append(table[b0 ushr 2])
      if (b1 >= 0) {
        sb.append(table[((b0 and 0x03) shl 4) or (b1 ushr 4)])
        if (b2 >= 0) {
          sb.append(table[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
          sb.append(table[b2 and 0x3F])
        } else {
          sb.append(table[(b1 and 0x0F) shl 2])
          sb.append('=')
        }
      } else {
        sb.append(table[(b0 and 0x03) shl 4])
        sb.append("==")
      }
      i += 3
    }
    return sb.toString()
  }

  // Minimal ASN.1 DER helpers
  private fun derTag(tag: Int, content: ByteArray): ByteArray {
    val lenBytes = encodeDerLength(content.size)
    return byteArrayOf(tag.toByte()) + lenBytes + content
  }

  private fun derSeq(content: ByteArray) = derTag(0x30, content)

  private fun derSet(content: ByteArray) = derTag(0x31, content)

  private fun derNull() = byteArrayOf(0x05, 0x00)

  private fun derOctetString(content: ByteArray) = derTag(0x04, content)

  private fun derBitString(content: ByteArray) = derTag(0x03, byteArrayOf(0x00) + content)

  private fun derUtf8String(text: String) = derTag(0x0C, text.toByteArray(Charsets.UTF_8))

  private fun derInteger(value: BigInteger) = derTag(0x02, value.toByteArray())

  private fun derUtcTime(date: Date): ByteArray {
    val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return derTag(0x17, fmt.format(date).toByteArray(Charsets.US_ASCII))
  }

  private fun derOid(vararg nodes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(nodes[0] * 40 + nodes[1])
    for (i in 2 until nodes.size) {
      var v = nodes[i]
      val stack = mutableListOf<Int>()
      stack.add(v and 0x7F)
      v = v ushr 7
      while (v > 0) {
        stack.add((v and 0x7F) or 0x80)
        v = v ushr 7
      }
      for (j in stack.size - 1 downTo 0) out.write(stack[j])
    }
    return derTag(0x06, out.toByteArray())
  }

  private fun encodeDerLength(len: Int): ByteArray {
    if (len < 128) return byteArrayOf(len.toByte())
    val bytes = mutableListOf<Byte>()
    var v = len
    while (v > 0) {
      bytes.add(0, (v and 0xFF).toByte())
      v = v ushr 8
    }
    return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
  }
}
