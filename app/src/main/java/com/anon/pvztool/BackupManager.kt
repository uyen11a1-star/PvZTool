package com.anon.pvztool

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    fun backupDir(): File {
        val d = File(Const.BACKUP_DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    // Copy pp.dat ra Download/pvz_backups/pp.dat.YYYYMMDD_HHMMSS.bak
    fun backup(srcPath: String, label: String = ""): String {
        if (!File(srcPath).exists()) return "ERR: file không tồn tại"
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "pp.dat.${stamp}${if (label.isNotBlank()) ".$label" else ""}.bak"
        val dst = File(backupDir(), name)
        return try {
            val src = ShizukuShell.translate(srcPath)
            val res = ShizukuShell.exec("cp ${ShizukuShell.quote(src)} ${ShizukuShell.quote(dst.absolutePath)} 2>&1")
            if (res.contains("Permission denied", true) || res.startsWith("ERR")) res
            else "OK: ${dst.absolutePath}"
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    // Copy 1 backup về pp.dat
    fun restore(backupPath: String, dstPath: String): String {
        if (!File(backupPath).exists()) return "ERR: backup không tồn tại"
        return try {
            val dst = ShizukuShell.translate(dstPath)
            val res = ShizukuShell.exec("cp ${ShizukuShell.quote(backupPath)} ${ShizukuShell.quote(dst)} 2>&1")
            if (res.contains("Permission denied", true) || res.startsWith("ERR")) res
            else "OK"
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    fun listBackups(): List<File> {
        val d = backupDir()
        if (!d.exists()) return emptyList()
        return (d.listFiles()?.filter { it.name.startsWith("pp.dat") } ?: emptyList())
            .sortedByDescending { it.lastModified() }
    }

    fun delete(path: String): Boolean = try { File(path).delete() } catch (_: Exception) { false }

    fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun fmtDate(t: Long): String =
        SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date(t))
}
