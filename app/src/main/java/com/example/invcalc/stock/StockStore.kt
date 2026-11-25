package com.example.invcalc.stock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 以 SharedPreferences 保存自選股清單（順序即清單順序）。 */
class StockStore(context: Context) {
    private val prefs = context.getSharedPreferences("stocks", Context.MODE_PRIVATE)

    fun load(): List<Stock> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Stock(o.getString("symbol"), o.optString("name", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Stock>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("symbol", it.symbol).put("name", it.name)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
