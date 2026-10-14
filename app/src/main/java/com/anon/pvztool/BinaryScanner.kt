package com.anon.pvztool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BinaryScanner {

    data class Match(
        val offset: Int,
        val bytesHex: String,
        val preview: String,
        val label: String = "Unknown",
        val decodedValue: Long = -1L,
        val keyChar: String = ""
    )

    enum class ValueType(val label: String, val size: Int) {
        INT8("int8", 1),
        INT16LE("int16 LE", 2),
        INT16BE("int16 BE", 2),
        INT32LE("int32 LE", 4),
        INT32BE("int32 BE", 4),
        INT64LE("int64 LE", 8),
        INT64BE("int64 BE", 8),
        FLOATLE("float LE", 4),
        FLOATBE("float BE", 4),
        DOUBLELE("double LE", 8),
        DOUBLEBE("double BE", 8),
        VARINT("varint (LEB128)", -1),
        VARINT_ZIGZAG("varint zigzag", -1),
        ASCII_STR("ASCII string", -1),
        UTF16LE_STR("UTF-16 LE string", -1)
    }

    fun scanInt(file: File, value: Long, type: ValueType): List<Match> {
        if (!file.exists()) return emptyList()
        val size = type.size
        val pattern = encodeInt(value, type)
        return scanPattern(file, pattern, size)
    }

    fun scanFloat(file: File, value: Float, type: ValueType): List<Match> {
        if (!file.exists()) return emptyList()
        val pattern = encodeFloat(value, type)
        return scanPattern(file, pattern, pattern.size)
    }

    // LEB128 varint encoding
    fun encodeVarint(value: Long, zigzag: Boolean): ByteArray {
        val v = if (zigzag) {
            (value shl 1) xor (value shr 63)
        } else value
        val out = mutableListOf<Byte>()
        var x = v
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) { out.add(b.toByte()); break }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }

    fun scanVarint(file: File, value: Long, zigzag: Boolean): List<Match> {
        if (!file.exists()) return emptyList()
        val pattern = encodeVarint(value, zigzag)
        return scanPattern(file, pattern, pattern.size)
    }

    fun scanAscii(file: File, text: String): List<Match> {
        if (!file.exists()) return emptyList()
        val pattern = text.toByteArray(Charsets.US_ASCII)
        return scanPattern(file, pattern, pattern.size)
    }

    fun scanUtf16Le(file: File, text: String): List<Match> {
        if (!file.exists()) return emptyList()
        val bb = ByteBuffer.allocate(text.length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (c in text) bb.putChar(c)
        return scanPattern(file, bb.array(), bb.array().size)
    }

    private fun scanPattern(file: File, pattern: ByteArray, size: Int): List<Match> {
        val matches = mutableListOf<Match>()
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length().toInt()
            val buf = ByteArray(65536)
            var filePos = 0
            var carry = ByteArray(0)

            while (filePos < len) {
                raf.seek(filePos.toLong())
                val read = raf.read(buf)
                if (read <= 0) break

                val window = carry + buf.copyOf(read)
                val baseOffset = filePos - carry.size

                var i = 0
                while (i <= window.size - pattern.size) {
                    var ok = true
                    for (j in 0 until pattern.size) {
                        if (window[i + j] != pattern[j]) { ok = false; break }
                    }
                    if (ok) {
                        val absOff = baseOffset + i
                        matches.add(makeMatch(file, absOff, size))
                        if (matches.size >= 200) return matches
                    }
                    i++
                }

                carry = window.copyOfRange(window.size - (pattern.size - 1).coerceAtLeast(0), window.size)
                filePos += read
            }
        }
        return matches
    }

    private fun makeMatch(file: File, offset: Int, size: Int): Match {
        val s = if (size < 0) 4 else size
        val start = (offset - 4).coerceAtLeast(0)
        val end = (offset + s + 12).coerceAtMost(file.length().toInt())
        val buf = ByteArray(end - start)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start.toLong())
            raf.readFully(buf)
        }
        val hex = buf.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val ascii = buf.map { b ->
            val c = b.toInt() and 0xFF
            if (c in 32..126) c.toChar() else '.'
        }.joinToString("")
        val (label, keyChar) = identifyKey(file, offset)
        val decoded = decodeValueAt(file, offset)
        return Match(offset, hex, ascii, label, decoded, keyChar)
    }

    // Key map
    private val KEY_MAP = mapOf(
        "c" to "Coin",
        "g" to "Gems",
        "t" to "Trophy",
        "tc" to "Trophy",
        "m" to "Mints",
        "s" to "Stars",
        "pf" to "Penny Fuel",
        "zs" to "Zen Sun",
        "ga" to "Gauntlet",
        "pt" to "Penny Tickets",
        "n" to "Player Name",
        "i" to "Current Level",
        "v" to "Version",
        "uid" to "UID",
        "objclass" to "objclass",
        "objdata" to "objdata",
        "tei" to "tei",
        "utl" to "utl",
        "mfd" to "mfd",
        "lsc" to "lsc",
        "ltfet" to "ltfet"
    )

    // Đọc ngược từ offset để tìm pattern `90 01 <char> 24` hoặc `90 0N <key> 24`
    private fun identifyKey(file: File, offset: Int): Pair<String, String> {
        try {
            // Đọc 32 byte trước offset
            val back = 32
            val start = (offset - back).coerceAtLeast(0)
            val len = offset - start
            if (len < 3) return "Unknown" to ""
            val buf = ByteArray(len)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start.toLong())
                raf.readFully(buf)
            }

            // Tìm tag key gần nhất: 0x90 0xNN <N bytes ASCII>
            for (i in (buf.size - 2) downTo 0) {
                val tag = buf[i].toInt() and 0xFF
                if (tag == 0x90 && i + 2 < buf.size) {
                    val keyLen = buf[i + 1].toInt() and 0xFF
                    if (keyLen in 1..20 && i + 2 + keyLen <= buf.size) {
                        val keyBytes = buf.copyOfRange(i + 2, i + 2 + keyLen)
                        val key = String(keyBytes, Charsets.US_ASCII)
                        if (key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }) {
                            val label = KEY_MAP[key] ?: key
                            return label to key
                        }
                    }
                }
            }
            // Fallback: pattern 0x91 <keyLen u16le> <key>
            for (i in (buf.size - 4) downTo 0) {
                val tag = buf[i].toInt() and 0xFF
                if (tag == 0x91 && i + 4 < buf.size) {
                    val keyLen = (buf[i + 1].toInt() and 0xFF) or ((buf[i + 2].toInt() and 0xFF) shl 8)
                    if (keyLen in 1..30 && i + 3 + keyLen <= buf.size) {
                        val keyBytes = buf.copyOfRange(i + 3, i + 3 + keyLen)
                        val key = String(keyBytes, Charsets.US_ASCII)
                        if (key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }) {
                            val label = KEY_MAP[key] ?: key
                            return label to key
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "Unknown" to ""
    }

    // Decode varint value tại offset
    private fun decodeValueAt(file: File, offset: Int): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset.toLong())
                var result = 0L
                var shift = 0
                var bytesRead = 0
                while (shift <= 35 && bytesRead < 5) {
                    val b = raf.read()
                    if (b < 0) break
                    bytesRead++
                    result = result or ((b.toLong() and 0x7F) shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
        } catch (_: Exception) {}
        return -1L
    }

    private fun encodeInt(value: Long, type: ValueType): ByteArray {
        val bb = ByteBuffer.allocate(type.size)
        when (type) {
            ValueType.INT8 -> return byteArrayOf((value and 0xFF).toByte())
            ValueType.INT16LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putShort((value and 0xFFFF).toShort())
            ValueType.INT16BE -> bb.order(ByteOrder.BIG_ENDIAN).putShort((value and 0xFFFF).toShort())
            ValueType.INT32LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt())
            ValueType.INT32BE -> bb.order(ByteOrder.BIG_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt())
            ValueType.INT64LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putLong(value)
            ValueType.INT64BE -> bb.order(ByteOrder.BIG_ENDIAN).putLong(value)
            ValueType.FLOATLE -> bb.order(ByteOrder.LITTLE_ENDIAN).putFloat(value.toFloat())
            ValueType.FLOATBE -> bb.order(ByteOrder.BIG_ENDIAN).putFloat(value.toFloat())
            ValueType.DOUBLELE -> bb.order(ByteOrder.LITTLE_ENDIAN).putDouble(value.toDouble())
            ValueType.DOUBLEBE -> bb.order(ByteOrder.BIG_ENDIAN).putDouble(value.toDouble())
            else -> return ByteArray(0)
        }
        return bb.array()
    }

    private fun encodeFloat(value: Float, type: ValueType): ByteArray {
        val bb = ByteBuffer.allocate(type.size)
        when (type) {
            ValueType.FLOATLE -> bb.order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
            ValueType.FLOATBE -> bb.order(ByteOrder.BIG_ENDIAN).putFloat(value)
            ValueType.DOUBLELE -> bb.order(ByteOrder.LITTLE_ENDIAN).putDouble(value.toDouble())
            ValueType.DOUBLEBE -> bb.order(ByteOrder.BIG_ENDIAN).putDouble(value.toDouble())
            else -> error("type không phải float")
        }
        return bb.array()
    }
}
