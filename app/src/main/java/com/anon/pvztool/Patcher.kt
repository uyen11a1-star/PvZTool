package com.anon.pvztool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Patcher {

    // Đọc giá trị tại offset theo type
    fun read(file: File, offset: Int, type: BinaryScanner.ValueType): Any? {
        if (!file.exists()) return null
        if (offset < 0 || offset + type.size > file.length()) return null
        val buf = ByteArray(type.size)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset.toLong())
            raf.readFully(buf)
        }
        val bb = ByteBuffer.wrap(buf)
        return when (type) {
            BinaryScanner.ValueType.INT8 -> (buf[0].toInt() and 0xFF).toLong()
            BinaryScanner.ValueType.INT16LE -> bb.order(ByteOrder.LITTLE_ENDIAN).short.toLong()
            BinaryScanner.ValueType.INT16BE -> bb.order(ByteOrder.BIG_ENDIAN).short.toLong()
            BinaryScanner.ValueType.INT32LE -> bb.order(ByteOrder.LITTLE_ENDIAN).int.toLong()
            BinaryScanner.ValueType.INT32BE -> bb.order(ByteOrder.BIG_ENDIAN).int.toLong()
            BinaryScanner.ValueType.INT64LE -> bb.order(ByteOrder.LITTLE_ENDIAN).long
            BinaryScanner.ValueType.INT64BE -> bb.order(ByteOrder.BIG_ENDIAN).long
            BinaryScanner.ValueType.FLOATLE -> bb.order(ByteOrder.LITTLE_ENDIAN).float
            BinaryScanner.ValueType.FLOATBE -> bb.order(ByteOrder.BIG_ENDIAN).float
            BinaryScanner.ValueType.DOUBLELE -> bb.order(ByteOrder.LITTLE_ENDIAN).double
            BinaryScanner.ValueType.DOUBLEBE -> bb.order(ByteOrder.BIG_ENDIAN).double
        }
    }

    // Patch giá trị mới tại offset — giữ nguyên độ dài byte
    fun writeInt(file: File, offset: Int, value: Long, type: BinaryScanner.ValueType): String {
        if (!file.exists()) return "ERR: file không tồn tại"
        if (offset < 0 || offset + type.size > file.length()) return "ERR: offset vượt file"
        val bb = ByteBuffer.allocate(type.size)
        when (type) {
            BinaryScanner.ValueType.INT8 -> bb.put((value and 0xFF).toByte())
            BinaryScanner.ValueType.INT16LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putShort((value and 0xFFFF).toShort())
            BinaryScanner.ValueType.INT16BE -> bb.order(ByteOrder.BIG_ENDIAN).putShort((value and 0xFFFF).toShort())
            BinaryScanner.ValueType.INT32LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt())
            BinaryScanner.ValueType.INT32BE -> bb.order(ByteOrder.BIG_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt())
            BinaryScanner.ValueType.INT64LE -> bb.order(ByteOrder.LITTLE_ENDIAN).putLong(value)
            BinaryScanner.ValueType.INT64BE -> bb.order(ByteOrder.BIG_ENDIAN).putLong(value)
            else -> return "ERR: type không phải int"
        }
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset.toLong())
                raf.write(bb.array())
            }
            "OK"
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    fun writeFloat(file: File, offset: Int, value: Float, type: BinaryScanner.ValueType): String {
        if (!file.exists()) return "ERR: file không tồn tại"
        if (offset < 0 || offset + type.size > file.length()) return "ERR: offset vượt file"
        val bb = ByteBuffer.allocate(type.size)
        when (type) {
            BinaryScanner.ValueType.FLOATLE -> bb.order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
            BinaryScanner.ValueType.FLOATBE -> bb.order(ByteOrder.BIG_ENDIAN).putFloat(value)
            BinaryScanner.ValueType.DOUBLELE -> bb.order(ByteOrder.LITTLE_ENDIAN).putDouble(value.toDouble())
            BinaryScanner.ValueType.DOUBLEBE -> bb.order(ByteOrder.BIG_ENDIAN).putDouble(value.toDouble())
            else -> return "ERR: type không phải float"
        }
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset.toLong())
                raf.write(bb.array())
            }
            "OK"
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    // Copy file ra ngoài + patch + copy ngược (dùng cho protected path)
    fun patchProtected(localPath: String, remotePath: String, offset: Int, value: Long, type: BinaryScanner.ValueType, isFloat: Boolean): String {
        // localPath: file đã copy ra Download
        val err = if (isFloat) writeFloat(File(localPath), offset, value.toFloat(), type)
                  else writeInt(File(localPath), offset, value, type)
        if (err != "OK") return err
        // Copy ngược
        val remote = ShizukuShell.translate(remotePath)
        val res = ShizukuShell.exec("cp ${ShizukuShell.quote(localPath)} ${ShizukuShell.quote(remote)} 2>&1")
        return if (res.contains("Permission denied", true) || res.startsWith("ERR")) res else "OK"
    }
}
