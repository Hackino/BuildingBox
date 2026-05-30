package com.buildingbox.app.core.money

/**
 * Dual-currency amount. USD and LBP are two independent pots — never converted.
 * Stored as integers: [usdCents] (1500 = $15.00) and [lbp] (whole pounds).
 */
data class DualAmount(val usdCents: Long = 0, val lbp: Long = 0) {
    operator fun plus(other: DualAmount) = DualAmount(usdCents + other.usdCents, lbp + other.lbp)
    operator fun minus(other: DualAmount) = DualAmount(usdCents - other.usdCents, lbp - other.lbp)
    val hasUsd: Boolean get() = usdCents != 0L
    val hasLbp: Boolean get() = lbp != 0L
    val isEmpty: Boolean get() = !hasUsd && !hasLbp

    companion object {
        val ZERO = DualAmount(0, 0)
        fun usd(dollars: Double) = DualAmount(usdCents = (dollars * 100).toLong())
        fun of(usdCents: Long, lbp: Long) = DualAmount(usdCents, lbp)
    }
}

fun Iterable<DualAmount>.sum(): DualAmount =
    fold(DualAmount.ZERO) { acc, m -> acc + m }

/** "$1,500" / compact "$1.5K". */
fun formatUsd(usdCents: Long, compact: Boolean = false): String {
    val dollars = usdCents / 100.0
    if (compact && kotlin.math.abs(dollars) >= 10_000) return "$" + compactNumber(dollars)
    val whole = usdCents / 100
    val cents = (kotlin.math.abs(usdCents) % 100).toInt()
    val grouped = groupThousands(whole)
    return if (cents == 0) "$$grouped" else "$$grouped.${cents.toString().padStart(2, '0')}"
}

/** "1,500,000 LL" / compact "1.5M LL". */
fun formatLbp(lbp: Long, compact: Boolean = false): String =
    if (compact) "${compactNumber(lbp.toDouble())} LL" else "${groupThousands(lbp)} LL"

private fun groupThousands(value: Long): String {
    val neg = value < 0
    val digits = kotlin.math.abs(value).toString()
    val sb = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return if (neg) "-$sb" else sb.toString()
}

private fun compactNumber(n: Double): String {
    val abs = kotlin.math.abs(n)
    val sign = if (n < 0) "-" else ""
    return when {
        abs >= 1_000_000 -> sign + trim(abs / 1_000_000) + "M"
        abs >= 1_000 -> sign + trim(abs / 1_000) + "K"
        else -> sign + abs.toLong().toString()
    }
}

private fun trim(v: Double): String {
    val oneDp = (kotlin.math.round(v * 10) / 10.0)
    return if (oneDp % 1.0 == 0.0) oneDp.toLong().toString() else oneDp.toString()
}
