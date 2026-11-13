package com.example.apkbuilder

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ResourcesArscWriter {
  private const val RES_TABLE_TYPE = 0x0002
  private const val RES_TABLE_PACKAGE_TYPE = 0x0200
  private const val RES_TABLE_TYPE_TYPE = 0x0201
  private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
  private const val TYPE_STRING = 0x03

  fun compileResources(
    packageName: String,
    appName: String,
    iconPathInApk: String = "res/mipmap-xxxhdpi-v4/ic_launcher.png",
  ): ByteArray {
    val globalPool = AxmlManifestWriter.buildUtf16StringPool(listOf(appName, iconPathInApk))
    val typeStringsPool = AxmlManifestWriter.buildUtf16StringPool(listOf("string", "mipmap"))
    val keyStringsPool = AxmlManifestWriter.buildUtf16StringPool(listOf("app_name", "ic_launcher"))

    val stringTypeSpec = buildTypeSpecChunk(typeId = 1)
    val stringTypeChunk = buildTypeChunk(typeId = 1, keyIdx = 0, globalStringIdx = 0)
    val mipmapTypeSpec = buildTypeSpecChunk(typeId = 2)
    val mipmapTypeChunk = buildTypeChunk(typeId = 2, keyIdx = 1, globalStringIdx = 1)

    val pkgHeaderSize = 288
    val typeStringsOffset = pkgHeaderSize
    val keyStringsOffset = typeStringsOffset + typeStringsPool.size
    val pkgBodySize =
      typeStringsPool.size +
        keyStringsPool.size +
        stringTypeSpec.size +
        stringTypeChunk.size +
        mipmapTypeSpec.size +
        mipmapTypeChunk.size
    val pkgChunkSize = pkgHeaderSize + pkgBodySize

    val pkgBuf = ByteBuffer.allocate(pkgChunkSize).order(ByteOrder.LITTLE_ENDIAN)
    pkgBuf.putShort(RES_TABLE_PACKAGE_TYPE.toShort())
    pkgBuf.putShort(pkgHeaderSize.toShort())
    pkgBuf.putInt(pkgChunkSize)
    pkgBuf.putInt(0x7f) // package ID

    // 128 UTF-16 chars = 256 bytes
    val pkgChars = packageName.take(127).toCharArray()
    for (i in 0 until 128) {
      val ch = if (i < pkgChars.size) pkgChars[i].code else 0
      pkgBuf.putShort(ch.toShort())
    }

    pkgBuf.putInt(typeStringsOffset)
    pkgBuf.putInt(2) // lastPublicType
    pkgBuf.putInt(keyStringsOffset)
    pkgBuf.putInt(2) // lastPublicKey
    pkgBuf.putInt(0) // typeIdOffset

    pkgBuf.put(typeStringsPool)
    pkgBuf.put(keyStringsPool)
    pkgBuf.put(stringTypeSpec)
    pkgBuf.put(stringTypeChunk)
    pkgBuf.put(mipmapTypeSpec)
    pkgBuf.put(mipmapTypeChunk)

    val pkgBytes = pkgBuf.array()
    val totalSize = 12 + globalPool.size + pkgBytes.size
    val tableBuf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    tableBuf.putShort(RES_TABLE_TYPE.toShort())
    tableBuf.putShort(12)
    tableBuf.putInt(totalSize)
    tableBuf.putInt(1) // packageCount
    tableBuf.put(globalPool)
    tableBuf.put(pkgBytes)
    return tableBuf.array()
  }

  private fun buildTypeSpecChunk(typeId: Int): ByteArray {
    val size = 20 // 16 header + 1 entry flag (4 bytes)
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(RES_TABLE_TYPE_SPEC_TYPE.toShort())
    buf.putShort(16)
    buf.putInt(size)
    buf.put(typeId.toByte())
    buf.put(0)
    buf.putShort(0)
    buf.putInt(1) // entryCount
    buf.putInt(0) // config flags
    return buf.array()
  }

  private fun buildTypeChunk(typeId: Int, keyIdx: Int, globalStringIdx: Int): ByteArray {
    val configSize = 56
    val headerSize = 20 + configSize // 76 bytes
    val entryOffsetArraySize = 4
    val entrySize = 16 // ResTable_entry (8) + Res_value (8)
    val entriesStart = headerSize + entryOffsetArraySize
    val totalSize = entriesStart + entrySize

    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(RES_TABLE_TYPE_TYPE.toShort())
    buf.putShort(headerSize.toShort())
    buf.putInt(totalSize)
    buf.put(typeId.toByte())
    buf.put(0) // flags
    buf.putShort(0) // reserved
    buf.putInt(1) // entryCount
    buf.putInt(entriesStart)

    // ResTable_config (56 bytes: size=56 followed by 52 zero bytes)
    buf.putInt(configSize)
    for (i in 0 until 52) buf.put(0)

    // Entry offset 0
    buf.putInt(0)

    // ResTable_entry
    buf.putShort(8) // size
    buf.putShort(0) // flags
    buf.putInt(keyIdx)

    // Res_value
    buf.putShort(8) // size
    buf.put(0) // res0
    buf.put(TYPE_STRING.toByte())
    buf.putInt(globalStringIdx)

    return buf.array()
  }
}
