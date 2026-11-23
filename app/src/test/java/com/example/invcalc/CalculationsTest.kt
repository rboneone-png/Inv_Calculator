package com.example.invcalc

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculationsTest {
    @Test
    fun compound_yearly() {
        val r = Calculations.compound(10000.0, 5.0, 10.0, 1)
        assertEquals(16288.95, r.futureValue, 0.01)
    }

    @Test
    fun dca_zeroRate() {
        val r = Calculations.dca(0.0, 1000.0, 0.0, 1.0)
        assertEquals(12000.0, r.futureValue, 0.001)
    }

    @Test
    fun returnRate_doubleInTwoYears() {
        val r = Calculations.returnRate(100.0, 200.0, 2.0)
        assertEquals(100.0, r.totalReturnPct, 0.001)
        assertEquals(41.42, r.cagrPct, 0.01)
    }

    @Test
    fun loan_oneYear() {
        val r = Calculations.loan(100000.0, 12.0, 1.0)
        assertEquals(8884.88, r.monthlyPayment, 0.01)
    }

    @Test
    fun loanByMonths_matchesYears() {
        val r = Calculations.loanByMonths(100000.0, 12.0, 12)
        assertEquals(8884.88, r.monthlyPayment, 0.01)
    }

    @Test
    fun remainingBalance_startAndEnd() {
        assertEquals(100000.0, Calculations.remainingBalance(100000.0, 12.0, 12, 0), 0.01)
        assertEquals(0.0, Calculations.remainingBalance(100000.0, 12.0, 12, 12), 0.01)
    }

    @Test
    fun grid_basic() {
        val r = Calculations.grid(120.0, 100.0, 4, 4000.0)
        assertEquals(5.0, r.step, 0.0001)
        assertEquals(1000.0, r.perGridCapital, 0.0001)
        assertEquals(4, r.levels.size)
        assertEquals(100.0, r.levels[0].buyPrice, 0.0001)
        assertEquals(105.0, r.levels[0].sellPrice, 0.0001)
        assertEquals(50.0, r.levels[0].profit, 0.0001)
        assertEquals(186.55, r.totalProfit, 0.01)
    }
}
