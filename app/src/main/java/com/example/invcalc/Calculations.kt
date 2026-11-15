package com.example.invcalc

import kotlin.math.pow
import kotlin.math.roundToInt

/** 純計算邏輯（不依賴 Android），方便單元測試。 */
object Calculations {

    data class CompoundResult(val futureValue: Double, val totalInterest: Double)

    /** 複利：本金 P、年利率 rate(%)、年數 years、每年複利次數 n。 */
    fun compound(principal: Double, ratePct: Double, years: Double, perYear: Int): CompoundResult {
        val r = ratePct / 100.0
        val fv = principal * (1 + r / perYear).pow(perYear * years)
        return CompoundResult(fv, fv - principal)
    }

    data class DcaResult(val futureValue: Double, val totalInvested: Double, val gain: Double)

    /** 定期定額：初始本金 + 每月投入（期末投入），年化報酬率 rate(%)。 */
    fun dca(initial: Double, monthly: Double, ratePct: Double, years: Double): DcaResult {
        val months = (years * 12).toInt()
        val i = ratePct / 100.0 / 12.0
        val growth = (1 + i).pow(months)
        val annuity = if (i == 0.0) monthly * months else monthly * (growth - 1) / i
        val fv = initial * growth + annuity
        val invested = initial + monthly * months
        return DcaResult(fv, invested, fv - invested)
    }

    data class ReturnResult(val profit: Double, val totalReturnPct: Double, val cagrPct: Double)

    /** 報酬率：總報酬率與年化報酬率（CAGR）。 */
    fun returnRate(initial: Double, finalValue: Double, years: Double): ReturnResult {
        val total = (finalValue / initial - 1) * 100
        val cagr = if (years > 0) ((finalValue / initial).pow(1 / years) - 1) * 100 else Double.NaN
        return ReturnResult(finalValue - initial, total, cagr)
    }

    data class LoanResult(val monthlyPayment: Double, val totalPayment: Double, val totalInterest: Double)

    /** 貸款：本息平均攤還（以年數輸入）。 */
    fun loan(amount: Double, ratePct: Double, years: Double): LoanResult =
        loanByMonths(amount, ratePct, (years * 12).roundToInt())

    /** 貸款：本息平均攤還（以期數輸入，每月一期）。 */
    fun loanByMonths(amount: Double, ratePct: Double, months: Int): LoanResult {
        val i = ratePct / 100.0 / 12.0
        val payment =
            if (i == 0.0) amount / months
            else amount * i * (1 + i).pow(months) / ((1 + i).pow(months) - 1)
        val total = payment * months
        return LoanResult(payment, total, total - amount)
    }

    /** 已繳 paid 期後的剩餘本金。 */
    fun remainingBalance(amount: Double, ratePct: Double, months: Int, paid: Int): Double {
        val i = ratePct / 100.0 / 12.0
        val payment = loanByMonths(amount, ratePct, months).monthlyPayment
        if (i == 0.0) return amount - payment * paid
        val g = (1 + i).pow(paid)
        return amount * g - payment * (g - 1) / i
    }
}
