package com.anon.pvztool

import android.content.Context

// Lưu offset của các field đã tìm được qua Scanner
object OffsetStore {

    private const val PREF = "pvztool_offsets"

    data class Field(
        val key: String,
        val offset: Int,
        val type: String,   // int8, int16le, int16be, int32le, int32be, int64le, int64be, floatle, floatbe, doublele, doublebe
        val length: Int
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(ctx: Context, key: String, offset: Int, type: String, length: Int) {
        prefs(ctx).edit()
            .putInt("${key}_off", offset)
            .putString("${key}_type", type)
            .putInt("${key}_len", length)
            .apply()
    }

    fun get(ctx: Context, key: String): Field? {
        val p = prefs(ctx)
        if (!p.contains("${key}_off")) return null
        return Field(
            key = key,
            offset = p.getInt("${key}_off", -1),
            type = p.getString("${key}_type", "int32le") ?: "int32le",
            length = p.getInt("${key}_len", 4)
        )
    }

    fun remove(ctx: Context, key: String) {
        prefs(ctx).edit()
            .remove("${key}_off")
            .remove("${key}_type")
            .remove("${key}_len")
            .apply()
    }

    fun allKeys(): List<String> = listOf("coin", "gem", "mint", "gauntlet", "pennyfuel", "zensun", "trophy")
}
