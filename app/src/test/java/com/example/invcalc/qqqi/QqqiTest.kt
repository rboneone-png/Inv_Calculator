package com.example.invcalc.qqqi

import com.example.invcalc.stock.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QqqiTest {

    @Test
    fun basicExample() {
        // A=100,000 NT、H=32、B=50 USD → 100000/32/50 = 62.5 → 62 股
        val r = QqqiCalc.compute(100000.0, 32.0, 50.0, 0.5)!!
        assertEquals(62L, r.shares)
        assertEquals(31.0, r.monthlyUsd, 1e-9)      // E = 62 × 0.5
        assertEquals(992L, r.monthlyNt)             // F = 31 × 32
        assertEquals(11904L, r.yearlyNt)            // G = 992 × 12
        assertEquals(62 * 50.0 * 32.0, r.investedNt, 1e-6)
    }

    @Test
    fun sharesAreTruncatedNotRounded() {
        // 9.99… 股也只能買 9 股
        assertEquals(9L, QqqiCalc.compute(99.99, 1.0, 10.0, 0.0)!!.shares)
        assertEquals(0L, QqqiCalc.compute(100.0, 32.0, 50.0, 0.5)!!.shares) // 不足 1 股
    }

    @Test
    fun floatingPointNoiseDoesNotBreakFloor() {
        // 浮點數 0.3 / 0.1 = 2.9999999999999996，直接 floor 會得到 2；BigDecimal 應得 3
        assertEquals(2.9999999999999996, 0.3 / 0.1, 0.0)
        assertEquals(3L, QqqiCalc.compute(0.3, 0.1, 1.0, 0.0)!!.shares)
    }

    @Test
    fun roundingIsHalfUp() {
        // E = 2 × 1.0 = 2、H = 52.25 → F = 104.5 → 四捨五入為 105（銀行家捨入會是 104）
        val r = QqqiCalc.compute(2 * 52.25 * 1.0, 52.25, 1.0, 1.0)!!
        assertEquals(2L, r.shares)
        assertEquals(105L, r.monthlyNt)
        assertEquals(105L * 12, r.yearlyNt)
        // 104.4999 → 104
        assertEquals(104L, QqqiCalc.compute(2 * 52.2495, 52.2495, 1.0, 1.0)!!.monthlyNt) // 104.499 → 104
    }

    @Test
    fun yearlyUsesRoundedMonthly() {
        // F 先四捨五入再乘 12：E = 3.333、H = 31.5 → F = 104.9895 → 105，G = 1260
        // （若用未取整的 F 計算則是 104.9895 × 12 = 1259.874 → 1260，此例相同；再看下例）
        val r = QqqiCalc.compute(3 * 31.5, 31.5, 1.0, 1.111)!!
        assertEquals(3L, r.shares)
        assertEquals(105L, r.monthlyNt)       // 3 × 1.111 × 31.5 = 104.9895
        assertEquals(1260L, r.yearlyNt)       // 105 × 12
        // 另一例：F 的小數 0.4 被捨去後再乘 12，與先乘 12 再取整不同
        val r2 = QqqiCalc.compute(10.0 * 1.0, 1.0, 1.0, 0.0404)!!   // E = 10 × 0.0404 = 0.404 → F = 0
        assertEquals(0L, r2.monthlyNt)
        assertEquals(0L, r2.yearlyNt)
    }

    @Test
    fun invalidInputs() {
        assertNull(QqqiCalc.compute(-1.0, 32.0, 50.0, 0.5))
        assertNull(QqqiCalc.compute(1000.0, 0.0, 50.0, 0.5))
        assertNull(QqqiCalc.compute(1000.0, 32.0, 0.0, 0.5))
        assertNull(QqqiCalc.compute(1000.0, 32.0, 50.0, -0.1))
        assertNull(QqqiCalc.compute(Double.NaN, 32.0, 50.0, 0.5))
        assertNotNull(QqqiCalc.compute(0.0, 32.0, 50.0, 0.5)) // 投入 0 → 0 股，仍可計算
    }

    @Test
    fun parseChartWithDividends() {
        val json = """
        {"chart":{"result":[{"meta":{"regularMarketPrice":51.23},
        "events":{"dividends":{
          "1700000000":{"amount":0.60,"date":1700000000},
          "1710000000":{"amount":0.50,"date":1710000000},
          "1720000000":{"amount":0.52,"date":1720000000},
          "1730000000":{"amount":0.48,"date":1730000000},
          "1740000000":{"amount":0.55,"date":1740000000},
          "1750000000":{"amount":0.56,"date":1750000000},
          "1760000000":{"amount":0.54,"date":1760000000},
          "1705000000":{"amount":0.0,"date":1705000000}}},
        "indicators":{"quote":[{"close":[50.0,null,51.0]}]}}],"error":null}}
        """.trimIndent()
        val info = QqqiApi.parse(json)
        assertEquals(51.23, info.price!!, 1e-12)
        assertEquals(7, info.dividends.size)                 // 金額為 0 的略過
        assertTrue(info.dividends.zipWithNext().all { (a, b) -> a.epochDay <= b.epochDay }) // 依日期排序
        // 最近 6 次：0.50, 0.52, 0.48, 0.55, 0.56, 0.54 → 平均 0.525
        assertEquals(6, QqqiApi.lastDividends(info.dividends).size)
        assertEquals(0.525, QqqiApi.averageDividend(info.dividends)!!, 1e-12)
        assertEquals(DateUtil.format(Math.floorDiv(1760000000L, 86400L)), DateUtil.format(info.dividends.last().epochDay))
    }

    @Test
    fun parseFallsBackToLastCloseAndHandlesErrors() {
        val noMeta = """{"chart":{"result":[{"meta":{},"indicators":{"quote":[{"close":[50.0,null,51.5,null]}]}}],"error":null}}"""
        assertEquals(51.5, QqqiApi.parse(noMeta).price!!, 1e-12)
        assertTrue(QqqiApi.parse(noMeta).dividends.isEmpty())
        assertNull(QqqiApi.averageDividend(emptyList()))
        // 不足 6 次就用現有的平均
        assertEquals(0.5, QqqiApi.averageDividend(listOf(Div(1, 0.4), Div(2, 0.6)))!!, 1e-12)
        var msg: String? = null
        try {
            QqqiApi.parse("""{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}""")
        } catch (e: IllegalArgumentException) {
            msg = e.message
        }
        assertEquals("No data found", msg)
    }

    @Test
    fun plainFormatting() {
        assertEquals("0.525", QqqiCalc.plain(0.525, 6))
        assertEquals("32", QqqiCalc.plain(32.0, 4))
        assertEquals("31.7425", QqqiCalc.plain(31.74250000001, 4))
        assertEquals("10000000", QqqiCalc.plain(1.0E7, 2))
    }
}
