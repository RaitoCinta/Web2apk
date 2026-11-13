package com.example.apkbuilder

import com.example.model.Web2ApkBuildRequest
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlManifestWriter {
  private const val ANDROID_NS_PREFIX = "android"
  private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"

  private const val RES_XML_TYPE = 0x0003
  private const val RES_STRING_POOL_TYPE = 0x0001
  private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
  private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
  private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
  private const val RES_XML_START_ELEMENT_TYPE = 0x0102
  private const val RES_XML_END_ELEMENT_TYPE = 0x0103

  private const val TYPE_REFERENCE = 0x01
  private const val TYPE_STRING = 0x03
  private const val TYPE_INT_DEC = 0x10
  private const val TYPE_INT_BOOLEAN = 0x12

  private val ATTR_RESOURCE_IDS =
    linkedMapOf(
      "theme" to 0x01010000,
      "label" to 0x01010001,
      "icon" to 0x01010002,
      "name" to 0x01010003,
      "exported" to 0x01010010,
      "screenOrientation" to 0x0101001e,
      "value" to 0x01010024,
      "minSdkVersion" to 0x0101020c,
      "versionCode" to 0x0101021b,
      "versionName" to 0x0101021c,
      "targetSdkVersion" to 0x01010270,
      "usesCleartextTraffic" to 0x010104ec,
    )

  fun generateReadableManifest(request: Web2ApkBuildRequest, resolvedTargetUrl: String): String {
    val pkg = request.packageName.trim().ifEmpty { "com.contoh.aplikasi" }
    val appName = request.appName.trim().ifEmpty { "Aplikasi Web" }.escapeXml()
    val versionName = request.versionName.trim().ifEmpty { "1.0.0" }.escapeXml()
    val permissionsXml =
      request.permissions.distinct().joinToString("\n") { perm ->
        "    <uses-permission android:name=\"${perm.escapeXml()}\" />"
      }
    val orientationAttr =
      request.orientation.manifestValue?.let { "\n            android:screenOrientation=\"$it\"" }
        ?: ""

    return buildString {
      appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
      appendLine("<manifest xmlns:android=\"$ANDROID_NS_URI\"")
      appendLine("    package=\"${pkg.escapeXml()}\"")
      appendLine("    android:versionCode=\"${request.versionCode}\"")
      appendLine("    android:versionName=\"$versionName\">")
      appendLine()
      appendLine("    <uses-sdk")
      appendLine("        android:minSdkVersion=\"24\"")
      appendLine("        android:targetSdkVersion=\"34\" />")
      appendLine()
      if (permissionsXml.isNotEmpty()) {
        appendLine(permissionsXml)
        appendLine()
      }
      appendLine("    <application")
      appendLine("        android:label=\"$appName\"")
      appendLine("        android:icon=\"@mipmap/ic_launcher\"")
      appendLine("        android:usesCleartextTraffic=\"true\">")
      appendLine()
      appendLine(
        "        <meta-data android:name=\"web2apk.url\" android:value=\"${resolvedTargetUrl.escapeXml()}\" />"
      )
      appendLine(
        "        <meta-data android:name=\"web2apk.js\" android:value=\"${request.jsEnabled}\" />"
      )
      appendLine(
        "        <meta-data android:name=\"web2apk.dom\" android:value=\"${request.domStorageEnabled}\" />"
      )
      appendLine(
        "        <meta-data android:name=\"web2apk.fullscreen\" android:value=\"${request.fullscreen}\" />"
      )
      appendLine(
        "        <meta-data android:name=\"web2apk.zoom\" android:value=\"${request.zoomEnabled}\" />"
      )
      appendLine()
      appendLine("        <activity")
      appendLine("            android:name=\"com.web2apk.runtime.WebAppActivity\"")
      appendLine("            android:label=\"$appName\"")
      appendLine("            android:exported=\"true\"$orientationAttr>")
      appendLine("            <intent-filter>")
      appendLine("                <action android:name=\"android.intent.action.MAIN\" />")
      appendLine("                <category android:name=\"android.intent.category.LAUNCHER\" />")
      appendLine("            </intent-filter>")
      appendLine("        </activity>")
      appendLine("    </application>")
      append("</manifest>")
    }
  }

  private sealed class AttrValue {
    data class Str(val value: String) : AttrValue()

    data class IntDec(val value: Int) : AttrValue()

    data class Bool(val value: Boolean) : AttrValue()

    data class Ref(val resId: Int) : AttrValue()
  }

  private data class Attr(val namespaceUri: String?, val name: String, val value: AttrValue)

  private sealed class XmlEvent {
    data class StartTag(val name: String, val attrs: List<Attr>, val line: Int) : XmlEvent()

    data class EndTag(val name: String, val line: Int) : XmlEvent()
  }

  fun compileBinaryManifest(
    request: Web2ApkBuildRequest,
    resolvedTargetUrl: String,
    appNameResId: Int = 0x7f010000,
    iconResId: Int = 0x7f020000,
  ): ByteArray {
    val pkg = request.packageName.trim()
    val appName = request.appName.trim()
    val versionName = request.versionName.trim().ifEmpty { "1.0.0" }
    val events = mutableListOf<XmlEvent>()
    var line = 2

    events +=
      XmlEvent.StartTag(
        name = "manifest",
        attrs =
          listOf(
            Attr(ANDROID_NS_URI, "versionCode", AttrValue.IntDec(request.versionCode)),
            Attr(ANDROID_NS_URI, "versionName", AttrValue.Str(versionName)),
            Attr(null, "package", AttrValue.Str(pkg)),
          ),
        line = line++,
      )

    events +=
      XmlEvent.StartTag(
        name = "uses-sdk",
        attrs =
          listOf(
            Attr(ANDROID_NS_URI, "minSdkVersion", AttrValue.IntDec(24)),
            Attr(ANDROID_NS_URI, "targetSdkVersion", AttrValue.IntDec(34)),
          ),
        line = line++,
      )
    events += XmlEvent.EndTag("uses-sdk", line++)

    request.permissions.distinct().forEach { perm ->
      events +=
        XmlEvent.StartTag(
          name = "uses-permission",
          attrs = listOf(Attr(ANDROID_NS_URI, "name", AttrValue.Str(perm))),
          line = line++,
        )
      events += XmlEvent.EndTag("uses-permission", line++)
    }

    events +=
      XmlEvent.StartTag(
        name = "application",
        attrs =
          listOf(
            Attr(ANDROID_NS_URI, "label", AttrValue.Ref(appNameResId)),
            Attr(ANDROID_NS_URI, "icon", AttrValue.Ref(iconResId)),
            Attr(ANDROID_NS_URI, "usesCleartextTraffic", AttrValue.Bool(true)),
          ),
        line = line++,
      )

    val metaEntries =
      listOf(
        "web2apk.url" to resolvedTargetUrl,
        "web2apk.js" to request.jsEnabled.toString(),
        "web2apk.dom" to request.domStorageEnabled.toString(),
        "web2apk.fullscreen" to request.fullscreen.toString(),
        "web2apk.zoom" to request.zoomEnabled.toString(),
      )
    metaEntries.forEach { (k, v) ->
      events +=
        XmlEvent.StartTag(
          name = "meta-data",
          attrs =
            listOf(
              Attr(ANDROID_NS_URI, "name", AttrValue.Str(k)),
              Attr(ANDROID_NS_URI, "value", AttrValue.Str(v)),
            ),
          line = line++,
        )
      events += XmlEvent.EndTag("meta-data", line++)
    }

    val activityAttrs = buildList {
      add(Attr(ANDROID_NS_URI, "name", AttrValue.Str("com.web2apk.runtime.WebAppActivity")))
      add(Attr(ANDROID_NS_URI, "label", AttrValue.Str(appName)))
      add(Attr(ANDROID_NS_URI, "exported", AttrValue.Bool(true)))
      val orientCode =
        when (request.orientation.manifestValue) {
          "portrait" -> 1
          "landscape" -> 0
          else -> null
        }
      if (orientCode != null) {
        add(Attr(ANDROID_NS_URI, "screenOrientation", AttrValue.IntDec(orientCode)))
      }
    }
    events += XmlEvent.StartTag("activity", activityAttrs, line++)

    events += XmlEvent.StartTag("intent-filter", emptyList(), line++)
    events +=
      XmlEvent.StartTag(
        "action",
        listOf(Attr(ANDROID_NS_URI, "name", AttrValue.Str("android.intent.action.MAIN"))),
        line++,
      )
    events += XmlEvent.EndTag("action", line++)
    events +=
      XmlEvent.StartTag(
        "category",
        listOf(Attr(ANDROID_NS_URI, "name", AttrValue.Str("android.intent.category.LAUNCHER"))),
        line++,
      )
    events += XmlEvent.EndTag("category", line++)
    events += XmlEvent.EndTag("intent-filter", line++)

    events += XmlEvent.EndTag("activity", line++)
    events += XmlEvent.EndTag("application", line++)
    events += XmlEvent.EndTag("manifest", line)

    // Build string pool with resource-mapped attribute names first
    val stringIndex = LinkedHashMap<String, Int>()
    fun intern(s: String): Int = stringIndex.getOrPut(s) { stringIndex.size }

    ATTR_RESOURCE_IDS.keys.forEach { intern(it) }
    intern(ANDROID_NS_PREFIX)
    intern(ANDROID_NS_URI)
    events.forEach { ev ->
      when (ev) {
        is XmlEvent.StartTag -> {
          intern(ev.name)
          ev.attrs.forEach { attr ->
            attr.namespaceUri?.let { intern(it) }
            intern(attr.name)
            if (attr.value is AttrValue.Str) {
              intern(attr.value.value)
            }
          }
        }
        is XmlEvent.EndTag -> intern(ev.name)
      }
    }

    val stringPoolBytes = buildUtf16StringPool(stringIndex.keys.toList())
    val resourceMapBytes = buildResourceMapChunk(ATTR_RESOURCE_IDS.values.toList())

    val bodyOut = ByteArrayOutputStream()
    writeNsChunk(
      bodyOut,
      RES_XML_START_NAMESPACE_TYPE,
      1,
      intern(ANDROID_NS_PREFIX),
      intern(ANDROID_NS_URI),
    )

    events.forEach { ev ->
      when (ev) {
        is XmlEvent.StartTag -> {
          val chunkSize = 36 + ev.attrs.size * 20
          val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
          buf.putShort(RES_XML_START_ELEMENT_TYPE.toShort())
          buf.putShort(16)
          buf.putInt(chunkSize)
          buf.putInt(ev.line)
          buf.putInt(-1)
          buf.putInt(-1)
          buf.putInt(intern(ev.name))
          buf.putShort(20)
          buf.putShort(20)
          buf.putShort(ev.attrs.size.toShort())
          buf.putShort(0)
          buf.putShort(0)
          buf.putShort(0)
          ev.attrs.forEach { attr ->
            val nsIdx = attr.namespaceUri?.let { intern(it) } ?: -1
            buf.putInt(nsIdx)
            buf.putInt(intern(attr.name))
            when (val v = attr.value) {
              is AttrValue.Str -> {
                val strIdx = intern(v.value)
                buf.putInt(strIdx)
                buf.putShort(8)
                buf.put(0)
                buf.put(TYPE_STRING.toByte())
                buf.putInt(strIdx)
              }
              is AttrValue.IntDec -> {
                buf.putInt(-1)
                buf.putShort(8)
                buf.put(0)
                buf.put(TYPE_INT_DEC.toByte())
                buf.putInt(v.value)
              }
              is AttrValue.Bool -> {
                buf.putInt(-1)
                buf.putShort(8)
                buf.put(0)
                buf.put(TYPE_INT_BOOLEAN.toByte())
                buf.putInt(if (v.value) -1 else 0)
              }
              is AttrValue.Ref -> {
                buf.putInt(-1)
                buf.putShort(8)
                buf.put(0)
                buf.put(TYPE_REFERENCE.toByte())
                buf.putInt(v.resId)
              }
            }
          }
          bodyOut.write(buf.array())
        }
        is XmlEvent.EndTag -> {
          val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
          buf.putShort(RES_XML_END_ELEMENT_TYPE.toShort())
          buf.putShort(16)
          buf.putInt(24)
          buf.putInt(ev.line)
          buf.putInt(-1)
          buf.putInt(-1)
          buf.putInt(intern(ev.name))
          bodyOut.write(buf.array())
        }
      }
    }

    writeNsChunk(
      bodyOut,
      RES_XML_END_NAMESPACE_TYPE,
      line,
      intern(ANDROID_NS_PREFIX),
      intern(ANDROID_NS_URI),
    )

    val bodyBytes = bodyOut.toByteArray()
    val totalSize = 8 + stringPoolBytes.size + resourceMapBytes.size + bodyBytes.size
    val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    out.putShort(RES_XML_TYPE.toShort())
    out.putShort(8)
    out.putInt(totalSize)
    out.put(stringPoolBytes)
    out.put(resourceMapBytes)
    out.put(bodyBytes)
    return out.array()
  }

  private fun writeNsChunk(
    out: ByteArrayOutputStream,
    type: Int,
    line: Int,
    prefixIdx: Int,
    uriIdx: Int,
  ) {
    val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(type.toShort())
    buf.putShort(16)
    buf.putInt(24)
    buf.putInt(line)
    buf.putInt(-1)
    buf.putInt(prefixIdx)
    buf.putInt(uriIdx)
    out.write(buf.array())
  }

  private fun buildResourceMapChunk(ids: List<Int>): ByteArray {
    val size = 8 + ids.size * 4
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
    buf.putShort(8)
    buf.putInt(size)
    ids.forEach { buf.putInt(it) }
    return buf.array()
  }

  internal fun buildUtf16StringPool(strings: List<String>): ByteArray {
    val offsets = IntArray(strings.size)
    val strData = ByteArrayOutputStream()
    strings.forEachIndexed { idx, str ->
      offsets[idx] = strData.size()
      val chars = str.toCharArray()
      val len = chars.size
      if (len > 0x7FFF) {
        val high = 0x8000 or ((len ushr 16) and 0x7FFF)
        val low = len and 0xFFFF
        strData.write(high and 0xFF)
        strData.write((high ushr 8) and 0xFF)
        strData.write(low and 0xFF)
        strData.write((low ushr 8) and 0xFF)
      } else {
        strData.write(len and 0xFF)
        strData.write((len ushr 8) and 0xFF)
      }
      chars.forEach { ch ->
        val code = ch.code
        strData.write(code and 0xFF)
        strData.write((code ushr 8) and 0xFF)
      }
      strData.write(0)
      strData.write(0)
    }
    while (strData.size() % 4 != 0) {
      strData.write(0)
    }

    val headerSize = 28
    val stringsStart = headerSize + offsets.size * 4
    val rawData = strData.toByteArray()
    val totalSize = stringsStart + rawData.size
    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(RES_STRING_POOL_TYPE.toShort())
    buf.putShort(headerSize.toShort())
    buf.putInt(totalSize)
    buf.putInt(strings.size)
    buf.putInt(0) // styleCount
    buf.putInt(0) // UTF-16LE
    buf.putInt(stringsStart)
    buf.putInt(0) // stylesStart
    offsets.forEach { buf.putInt(it) }
    buf.put(rawData)
    return buf.array()
  }

  private fun String.escapeXml(): String {
    return this.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }
}
