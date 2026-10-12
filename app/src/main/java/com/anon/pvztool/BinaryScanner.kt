package com.anon.pvztool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BinaryScanner {

    data class Match(
        val offset: Int,
        val bytesHex: String,     // 16 byte xung quanh offset
        val preview: String       // ASCII preview
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
        DOUBLEBE("double BE", 8)
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
        val start = (offset - 4).coerceAtLeast(0)
        val end = (offset + size + 12).coerceAtMost(file.length().toInt())
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
        return Match(offset, hex, ascii)
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
