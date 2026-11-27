package com.example.invcalc.stock

import java.util.Locale

data class Stock(val symbol: String, val name: String)

/** 一根 K 棒（日線，或重新取樣後的週／月／年線）。 */
data class Bar(
    val epochDay: Long,
    val close: Double,
    val adjClose: Double,
    val volume: Double,
    val open: Double = close,
    val high: Double = close,
    val low: Double = close
)

enum class Period(val label: String, val periodsPerYear: Int, val sharpeWindow: Int) {
    DAY("日線", 252, 60),
    WEEK("週線", 52, 26),
    MONTH("月線", 12, 12),
    YEAR("年線", 1, 3)
}

object DateUtil {
    /** epochDay（自 1970-01-01 起的天數）轉 (年, 月, 日)。 */
    fun civil(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468
        val era = Math.floorDiv(z, 146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    fun format(epochDay: Long): String {
        val (y, m, d) = civil(epochDay)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    /** "yyyy-MM-dd" → epochDay；格式不符回傳 null。 */
    fun parse(text: String): Long? {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(text.trim()) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        val yy = if (mo <= 2) y - 1 else y
        val era = Math.floorDiv(yy, 400)
        val yoe = yy - era * 400
        val mp = (mo + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    /** 星期一 = 0 … 星期日 = 6。 */
    fun dayOfWeek(epochDay: Long): Int = (((epochDay + 3) % 7) + 7).toInt() % 7
}

object SymbolUtil {
    private val TW_CODE = Regex("^[0-9]{4,6}[A-Z]?$")

    /** 台股代號：4–6 位數字，可帶一個字母結尾（如 00679B）。 */
    fun isTaiwanCode(s: String): Boolean = TW_CODE.matches(s.trim().uppercase(Locale.US))

    /** 台股代號自動加上 .TW，其餘維持原樣（轉大寫）。 */
    fun normalize(input: String): String {
        val s = input.trim().uppercase(Locale.US)
        return if (isTaiwanCode(s)) "$s.TW" else s
    }

    fun isTaiwanSymbol(symbol: String): Boolean = symbol.endsWith(".TW") || symbol.endsWith(".TWO")

    fun hasCjk(s: String): Boolean = s.any { it in '\u4e00'..'\u9fff' }
}
