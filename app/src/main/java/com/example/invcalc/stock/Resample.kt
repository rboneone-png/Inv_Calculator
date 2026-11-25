package com.example.invcalc.stock

object Resample {
    /** 日線 → 週／月／年線：收盤取該期最後一天，成交量加總。 */
    fun toPeriod(daily: List<Bar>, period: Period): List<Bar> {
        if (period == Period.DAY) return daily
        val out = ArrayList<Bar>()
        var curKey = Long.MIN_VALUE
        var last: Bar? = null
        var vol = 0.0
        for (b in daily) {
            val key = keyOf(b.epochDay, period)
            val prev = last
            if (key != curKey && prev != null) {
                out.add(prev.copy(volume = vol))
                vol = 0.0
            }
            curKey = key
            last = b
            vol += b.volume
        }
        last?.let { out.add(it.copy(volume = vol)) }
        return out
    }

    private fun keyOf(day: Long, p: Period): Long = when (p) {
        Period.DAY -> day
        Period.WEEK -> Math.floorDiv(day + 3, 7L) // 以星期一為一週起點
        Period.MONTH -> DateUtil.civil(day).let { it.first * 12L + it.second }
        Period.YEAR -> DateUtil.civil(day).first.toLong()
    }
}
