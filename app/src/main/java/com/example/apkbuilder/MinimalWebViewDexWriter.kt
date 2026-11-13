package com.example.apkbuilder

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * Generates a structurally valid Dalvik Executable (`classes.dex`, version 035)
 * containing `com.web2apk.runtime.WebAppActivity` extending `android.app.Activity`.
 */
object MinimalWebViewDexWriter {
  private data class ProtoKey(val ret: String, val params: List<String>)

  private data class MethodKey(val owner: String, val name: String, val proto: ProtoKey)

  fun buildWebViewDex(
    targetUrl: String,
    jsEnabled: Boolean,
    domStorageEnabled: Boolean,
    fullscreen: Boolean,
    zoomEnabled: Boolean,
  ): ByteArray {
    val activityDesc = "Lcom/web2apk/runtime/WebAppActivity;"
    val superDesc = "Landroid/app/Activity;"
    val bundleDesc = "Landroid/os/Bundle;"
    val webViewDesc = "Landroid/webkit/WebView;"
    val contextDesc = "Landroid/content/Context;"
    val webSettingsDesc = "Landroid/webkit/WebSettings;"
    val webClientDesc = "Landroid/webkit/WebViewClient;"
    val viewDesc = "Landroid/view/View;"
    val strDesc = "Ljava/lang/String;"

    val strings = linkedSetOf<String>()
    val types = linkedSetOf<String>()
    val protos = linkedSetOf<ProtoKey>()
    val methods = linkedSetOf<MethodKey>()

    fun addMethod(owner: String, name: String, ret: String, params: List<String>): MethodKey {
      val proto = ProtoKey(ret, params)
      val key = MethodKey(owner, name, proto)
      strings += owner
      strings += name
      strings += ret
      params.forEach { strings += it }
      types += owner
      types += ret
      params.forEach { types += it }
      protos += proto
      methods += key
      return key
    }

    strings += targetUrl
    types += activityDesc
    types += superDesc
    types += webViewDesc
    types += webClientDesc

    val mSuperInit = addMethod(superDesc, "<init>", "V", emptyList())
    val mSelfInit = addMethod(activityDesc, "<init>", "V", emptyList())
    val mSelfOnCreate = addMethod(activityDesc, "onCreate", "V", listOf(bundleDesc))
    val mSuperOnCreate = addMethod(superDesc, "onCreate", "V", listOf(bundleDesc))
    val mReqWindowFeature = addMethod(activityDesc, "requestWindowFeature", "Z", listOf("I"))
    val mWebViewInit = addMethod(webViewDesc, "<init>", "V", listOf(contextDesc))
    val mGetSettings = addMethod(webViewDesc, "getSettings", webSettingsDesc, emptyList())
    val mSetJs = addMethod(webSettingsDesc, "setJavaScriptEnabled", "V", listOf("Z"))
    val mSetDom = addMethod(webSettingsDesc, "setDomStorageEnabled", "V", listOf("Z"))
    val mSetZoom = addMethod(webSettingsDesc, "setBuiltInZoomControls", "V", listOf("Z"))
    val mWebClientInit = addMethod(webClientDesc, "<init>", "V", emptyList())
    val mSetWebClient = addMethod(webViewDesc, "setWebViewClient", "V", listOf(webClientDesc))
    val mLoadUrl = addMethod(webViewDesc, "loadUrl", "V", listOf(strDesc))
    val mSetContentView = addMethod(activityDesc, "setContentView", "V", listOf(viewDesc))

    // Add shorty strings for every proto
    val protoShorties =
      protos.associateWith { p ->
        buildString {
          append(shortyChar(p.ret))
          p.params.forEach { append(shortyChar(it)) }
        }.also { strings += it }
      }

    // DEX requires sorted string_ids (UTF-8 / MUTF-8 unsigned byte order)
    val sortedStrings = strings.sortedWith(Comparator { a, b -> compareMutf8(a, b) })
    val stringIdx = sortedStrings.withIndex().associate { it.value to it.index }

    val sortedTypes = types.sortedBy { stringIdx.getValue(it) }
    val typeIdx = sortedTypes.withIndex().associate { it.value to it.index }

    val sortedProtos =
      protos.sortedWith(
        compareBy<ProtoKey> { typeIdx.getValue(it.ret) }
          .thenBy { it.params.size }
          .thenComparator { a, b ->
            for (i in 0 until minOf(a.params.size, b.params.size)) {
              val cmp = typeIdx.getValue(a.params[i]).compareTo(typeIdx.getValue(b.params[i]))
              if (cmp != 0) return@thenComparator cmp
            }
            0
          }
      )
    val protoIdx = sortedProtos.withIndex().associate { it.value to it.index }

    val sortedMethods =
      methods.sortedWith(
        compareBy<MethodKey> { typeIdx.getValue(it.owner) }
          .thenBy { stringIdx.getValue(it.name) }
          .thenBy { protoIdx.getValue(it.proto) }
      )
    val methodIdx = sortedMethods.withIndex().associate { it.value to it.index }

    // Encode <init>()V code_item
    val initInsns =
      shortArrayOf(
        0x1070.toShort(),
        methodIdx.getValue(mSuperInit).toShort(),
        0x0000.toShort(), // p0 = v0
        0x000e.toShort(), // return-void
      )

    // Encode onCreate(Bundle)V code_item
    // Registers: v0, v1, v2 (locals), v3=this (p0), v4=bundle (p1)
    val onCreateInsnsList = mutableListOf<Short>()
    fun emit35c(opcode: Int, method: MethodKey, vararg regs: Int) {
      val a = regs.size
      val word0 = (a shl 12) or opcode
      val word1 = methodIdx.getValue(method)
      val c = if (a > 0) regs[0] else 0
      val d = if (a > 1) regs[1] else 0
      val e = if (a > 2) regs[2] else 0
      val f = if (a > 3) regs[3] else 0
      val word2 = (f shl 12) or (e shl 8) or (d shl 4) or c
      onCreateInsnsList += word0.toShort()
      onCreateInsnsList += word1.toShort()
      onCreateInsnsList += word2.toShort()
    }
    fun emit21c(opcode: Int, reg: Int, index: Int) {
      onCreateInsnsList += ((reg shl 8) or opcode).toShort()
      onCreateInsnsList += index.toShort()
    }
    fun emitConst4(reg: Int, literal: Int) {
      onCreateInsnsList += (((literal and 0xF) shl 12) or (reg shl 8) or 0x12).toShort()
    }

    // super.onCreate(savedInstanceState)
    emit35c(0x6f, mSuperOnCreate, 3, 4)
    if (fullscreen) {
      // requestWindowFeature(1)
      emitConst4(0, 1)
      emit35c(0x6e, mReqWindowFeature, 3, 0)
    }
    // v0 = new WebView(this)
    emit21c(0x22, 0, typeIdx.getValue(webViewDesc))
    emit35c(0x70, mWebViewInit, 0, 3)
    // v1 = v0.getSettings()
    emit35c(0x6e, mGetSettings, 0)
    onCreateInsnsList += ((1 shl 8) or 0x0c).toShort() // move-result-object v1
    // v1.setJavaScriptEnabled(jsEnabled)
    emitConst4(2, if (jsEnabled) 1 else 0)
    emit35c(0x6e, mSetJs, 1, 2)
    // v1.setDomStorageEnabled(domStorageEnabled)
    emitConst4(2, if (domStorageEnabled) 1 else 0)
    emit35c(0x6e, mSetDom, 1, 2)
    if (zoomEnabled) {
      emitConst4(2, 1)
      emit35c(0x6e, mSetZoom, 1, 2)
    }
    // v1 = new WebViewClient(); v0.setWebViewClient(v1)
    emit21c(0x22, 1, typeIdx.getValue(webClientDesc))
    emit35c(0x70, mWebClientInit, 1)
    emit35c(0x6e, mSetWebClient, 0, 1)
    // v1 = targetUrl; v0.loadUrl(v1)
    emit21c(0x1a, 1, stringIdx.getValue(targetUrl))
    emit35c(0x6e, mLoadUrl, 0, 1)
    // this.setContentView(v0)
    emit35c(0x6e, mSetContentView, 3, 0)
    // return-void
    onCreateInsnsList += 0x000e.toShort()

    val onCreateInsns = onCreateInsnsList.toShortArray()

    // Compute fixed table offsets
    val headerSize = 0x70
    val stringIdsOff = headerSize
    val typeIdsOff = stringIdsOff + sortedStrings.size * 4
    val protoIdsOff = typeIdsOff + sortedTypes.size * 4
    val fieldIdsOff = 0
    val methodIdsOff = protoIdsOff + sortedProtos.size * 12
    val classDefsOff = methodIdsOff + sortedMethods.size * 8
    val dataOff = classDefsOff + 32 // 1 class_def * 32 bytes

    val dataBuf = ByteArrayOutputStream()
    fun currentDataOffset(): Int = dataOff + dataBuf.size()
    fun align4() {
      while (currentDataOffset() % 4 != 0) dataBuf.write(0)
    }

    // 1. TypeLists for non-empty proto parameters (aligned 4)
    val protoParamOffsets = mutableMapOf<ProtoKey, Int>()
    sortedProtos.forEach { proto ->
      if (proto.params.isNotEmpty()) {
        align4()
        protoParamOffsets[proto] = currentDataOffset()
        writeIntLE(dataBuf, proto.params.size)
        proto.params.forEach { p -> writeShortLE(dataBuf, typeIdx.getValue(p)) }
      }
    }

    // 2. String data items
    val stringDataOffsets = IntArray(sortedStrings.size)
    sortedStrings.forEachIndexed { idx, s ->
      stringDataOffsets[idx] = currentDataOffset()
      val utf8 = s.toByteArray(Charsets.UTF_8)
      writeUleb128(dataBuf, s.length)
      dataBuf.write(utf8)
      dataBuf.write(0)
    }

    // 3. Code items (aligned 4)
    align4()
    val initCodeOffset = currentDataOffset()
    writeCodeItem(dataBuf, registersSize = 1, insSize = 1, outsSize = 1, insns = initInsns)

    align4()
    val onCreateCodeOffset = currentDataOffset()
    writeCodeItem(dataBuf, registersSize = 5, insSize = 2, outsSize = 2, insns = onCreateInsns)

    // 4. Class data item (ULEB128 encoded)
    val classDataOffset = currentDataOffset()
    writeUleb128(dataBuf, 0) // static_fields_size
    writeUleb128(dataBuf, 0) // instance_fields_size
    writeUleb128(dataBuf, 2) // direct_methods_size (<init>, onCreate)
    writeUleb128(dataBuf, 0) // virtual_methods_size

    val initMethodIdx = methodIdx.getValue(mSelfInit)
    val onCreateMethodIdx = methodIdx.getValue(mSelfOnCreate)
    // Ensure sorted method_idx order in direct_methods
    if (initMethodIdx < onCreateMethodIdx) {
      writeUleb128(dataBuf, initMethodIdx)
      writeUleb128(dataBuf, 0x10001) // PUBLIC | CONSTRUCTOR
      writeUleb128(dataBuf, initCodeOffset)

      writeUleb128(dataBuf, onCreateMethodIdx - initMethodIdx)
      writeUleb128(dataBuf, 0x0004) // PROTECTED
      writeUleb128(dataBuf, onCreateCodeOffset)
    } else {
      writeUleb128(dataBuf, onCreateMethodIdx)
      writeUleb128(dataBuf, 0x0004)
      writeUleb128(dataBuf, onCreateCodeOffset)

      writeUleb128(dataBuf, initMethodIdx - onCreateMethodIdx)
      writeUleb128(dataBuf, 0x10001)
      writeUleb128(dataBuf, initCodeOffset)
    }

    // 5. Map list (aligned 4)
    align4()
    val mapListOffset = currentDataOffset()
    val mapItems = buildList {
      add(Triple(0x0000, 1, 0)) // HEADER_ITEM
      add(Triple(0x0001, sortedStrings.size, stringIdsOff)) // STRING_ID_ITEM
      add(Triple(0x0002, sortedTypes.size, typeIdsOff)) // TYPE_ID_ITEM
      add(Triple(0x0003, sortedProtos.size, protoIdsOff)) // PROTO_ID_ITEM
      add(Triple(0x0005, sortedMethods.size, methodIdsOff)) // METHOD_ID_ITEM
      add(Triple(0x0006, 1, classDefsOff)) // CLASS_DEF_ITEM
      if (protoParamOffsets.isNotEmpty()) {
        add(Triple(0x1001, protoParamOffsets.size, protoParamOffsets.values.first())) // TYPE_LIST
      }
      add(Triple(0x2000, 1, classDataOffset)) // CLASS_DATA_ITEM
      add(Triple(0x2001, 2, initCodeOffset)) // CODE_ITEM
      add(Triple(0x2002, sortedStrings.size, stringDataOffsets.first())) // STRING_DATA_ITEM
      add(Triple(0x1000, 1, mapListOffset)) // MAP_LIST
    }
    writeIntLE(dataBuf, mapItems.size)
    mapItems.forEach { (type, count, off) ->
      writeShortLE(dataBuf, type)
      writeShortLE(dataBuf, 0)
      writeIntLE(dataBuf, count)
      writeIntLE(dataBuf, off)
    }

    val dataBytes = dataBuf.toByteArray()
    val totalFileSize = dataOff + dataBytes.size
    val out = ByteBuffer.allocate(totalFileSize).order(ByteOrder.LITTLE_ENDIAN)

    // Header (112 bytes)
    out.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
    out.putInt(0) // checksum placeholder
    out.put(ByteArray(20)) // SHA-1 placeholder
    out.putInt(totalFileSize)
    out.putInt(headerSize)
    out.putInt(0x12345678) // endian_tag
    out.putInt(0) // link_size
    out.putInt(0) // link_off
    out.putInt(mapListOffset)
    out.putInt(sortedStrings.size)
    out.putInt(stringIdsOff)
    out.putInt(sortedTypes.size)
    out.putInt(typeIdsOff)
    out.putInt(sortedProtos.size)
    out.putInt(protoIdsOff)
    out.putInt(0) // field_ids_size
    out.putInt(fieldIdsOff)
    out.putInt(sortedMethods.size)
    out.putInt(methodIdsOff)
    out.putInt(1) // class_defs_size
    out.putInt(classDefsOff)
    out.putInt(dataBytes.size)
    out.putInt(dataOff)

    // string_ids
    stringDataOffsets.forEach { out.putInt(it) }
    // type_ids
    sortedTypes.forEach { out.putInt(stringIdx.getValue(it)) }
    // proto_ids
    sortedProtos.forEach { p ->
      out.putInt(stringIdx.getValue(protoShorties.getValue(p)))
      out.putInt(typeIdx.getValue(p.ret))
      out.putInt(protoParamOffsets[p] ?: 0)
    }
    // method_ids
    sortedMethods.forEach { m ->
      out.putShort(typeIdx.getValue(m.owner).toShort())
      out.putShort(protoIdx.getValue(m.proto).toShort())
      out.putInt(stringIdx.getValue(m.name))
    }
    // class_def_item
    out.putInt(typeIdx.getValue(activityDesc))
    out.putInt(0x0001) // ACC_PUBLIC
    out.putInt(typeIdx.getValue(superDesc))
    out.putInt(0) // interfaces_off
    out.putInt(-1) // source_file_idx (NO_INDEX)
    out.putInt(0) // annotations_off
    out.putInt(classDataOffset)
    out.putInt(0) // static_values_off

    // data section
    out.put(dataBytes)

    val raw = out.array()
    // Compute SHA-1 over bytes [32..totalFileSize)
    val sha1 = MessageDigest.getInstance("SHA-1").digest(raw.copyOfRange(32, raw.size))
    System.arraycopy(sha1, 0, raw, 12, 20)

    // Compute Adler32 over bytes [12..totalFileSize)
    val adler = Adler32()
    adler.update(raw, 12, raw.size - 12)
    val checksum = adler.value.toInt()
    raw[8] = (checksum and 0xFF).toByte()
    raw[9] = ((checksum ushr 8) and 0xFF).toByte()
    raw[10] = ((checksum ushr 16) and 0xFF).toByte()
    raw[11] = ((checksum ushr 24) and 0xFF).toByte()

    return raw
  }

  private fun writeCodeItem(
    out: ByteArrayOutputStream,
    registersSize: Int,
    insSize: Int,
    outsSize: Int,
    insns: ShortArray,
  ) {
    writeShortLE(out, registersSize)
    writeShortLE(out, insSize)
    writeShortLE(out, outsSize)
    writeShortLE(out, 0) // tries_size
    writeIntLE(out, 0) // debug_info_off
    writeIntLE(out, insns.size)
    insns.forEach { writeShortLE(out, it.toInt()) }
  }

  private fun shortyChar(desc: String): Char =
    when {
      desc.startsWith("L") || desc.startsWith("[") -> 'L'
      else -> desc.first()
    }

  private fun compareMutf8(a: String, b: String): Int {
    val bytesA = a.toByteArray(Charsets.UTF_8)
    val bytesB = b.toByteArray(Charsets.UTF_8)
    val len = minOf(bytesA.size, bytesB.size)
    for (i in 0 until len) {
      val cmp = (bytesA[i].toInt() and 0xFF) - (bytesB[i].toInt() and 0xFF)
      if (cmp != 0) return cmp
    }
    return bytesA.size - bytesB.size
  }

  private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
    var v = value
    while (true) {
      val b = v and 0x7F
      v = v ushr 7
      if (v == 0) {
        out.write(b)
        break
      } else {
        out.write(b or 0x80)
      }
    }
  }

  private fun writeShortLE(out: ByteArrayOutputStream, v: Int) {
    out.write(v and 0xFF)
    out.write((v ushr 8) and 0xFF)
  }

  private fun writeIntLE(out: ByteArrayOutputStream, v: Int) {
    out.write(v and 0xFF)
    out.write((v ushr 8) and 0xFF)
    out.write((v ushr 16) and 0xFF)
    out.write((v ushr 24) and 0xFF)
  }
}
