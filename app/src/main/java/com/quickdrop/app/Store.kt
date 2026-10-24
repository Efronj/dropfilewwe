package com.quickdrop.app

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Tiny SharedPreferences-backed store: device name, visibility, trusted devices, history. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("quickdrop", Context.MODE_PRIVATE)

    private fun defaultName(): String = Build.MODEL ?: "Android"

    var nickname: String
        get() = p.getString("nick", null) ?: defaultName()
        set(v) {
            p.edit().putString("nick", v.trim().ifEmpty { defaultName() }).apply()
        }

    var visible: Boolean
        get() = p.getBoolean("visible", true)
        set(v) {
            p.edit().putBoolean("visible", v).apply()
        }

    var trusted: Set<String>
        get() = HashSet(p.getStringSet("trusted", emptySet()) ?: emptySet())
        set(v) {
            p.edit().putStringSet("trusted", HashSet(v)).apply()
        }

    fun history(): List<HistoryItem> {
        val arr = try {
            JSONArray(p.getString("history", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<HistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                HistoryItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    size = o.optLong("size"),
                    mime = o.optString("mime"),
                    outgoing = o.optBoolean("out"),
                    peer = o.optString("peer"),
                    time = o.optLong("time"),
                    uri = o.optString("uri").ifEmpty { null },
                    text = o.optString("text").ifEmpty { null },
                )
            )
        }
        return out
    }

    fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        for (h in list.take(200)) {
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("size", h.size)
                    .put("mime", h.mime)
                    .put("out", h.outgoing)
                    .put("peer", h.peer)
                    .put("time", h.time)
                    .put("uri", h.uri ?: "")
                    .put("text", h.text ?: "")
            )
        }
        p.edit().putString("history", arr.toString()).apply()
    }
}
