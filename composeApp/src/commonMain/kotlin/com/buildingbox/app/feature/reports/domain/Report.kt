package com.buildingbox.app.feature.reports.domain

import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.ExpenseCategory
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import com.buildingbox.app.feature.payments.domain.aggregate
import com.buildingbox.app.feature.units.domain.Apartment
import kotlinx.serialization.Serializable

/** /building meta. */
@Serializable
data class BuildingDto(
    val name: String = "Residence",
    val address: String = "",
    val currentMonth: String = "",
)

data class PaidEntry(val name: String, val owner: String, val amount: DualAmount, val partial: Boolean)

data class ReportData(
    val buildingName: String,
    val address: String,
    val month: String,
    val collected: DualAmount,
    val expected: DualAmount,
    val outstanding: DualAmount,
    val paidCount: Int,
    val total: Int,
    val expenses: List<Pair<ExpenseCategory, DualAmount>>,
    val totalSpent: DualAmount,
    val paidList: List<PaidEntry>,
    val unpaid: List<Pair<String, String>>,
    val opening: DualAmount,
    val net: DualAmount,
    val closing: DualAmount,
) {
    val isGain: Boolean get() = net.usdCents + net.lbp >= 0
}

fun buildReport(
    apts: List<Apartment>,
    allDues: List<Due>,
    allExp: List<Expense>,
    building: BuildingDto?,
    month: String,
): ReportData {
    val monthDues = allDues.filter { it.month == month }
    val months = apts.map { a -> a to aggregate(a.id, month, monthDues.filter { it.apartmentId == a.id }) }

    val expected = months.fold(DualAmount.ZERO) { acc, (_, am) -> acc + am.total }
    val collected = months.fold(DualAmount.ZERO) { acc, (_, am) -> acc + am.paid }

    val expensesByCat = allExp.filter { it.month == month }
        .groupBy { it.category }
        .map { (cat, list) -> cat to list.fold(DualAmount.ZERO) { a, e -> a + e.amount } }
        .sortedByDescending { it.second.usdCents + it.second.lbp / 1_000_000 }
    val totalSpent = expensesByCat.fold(DualAmount.ZERO) { a, (_, amt) -> a + amt }

    val paidList = months.filter { it.second.status != PaymentStatus.UNPAID }
        .map { (a, am) -> PaidEntry(a.name, a.ownerName, am.paid, am.status == PaymentStatus.PARTIAL) }
    val unpaid = months.filter { it.second.status != PaymentStatus.PAID }.map { (a, _) -> a.name to a.ownerName }

    // Balance in the box at the end of any month = paid dues − expenses up to and including it.
    fun balanceAsOf(m: String): DualAmount =
        allDues.filter { it.paid && it.month <= m }.fold(DualAmount.ZERO) { a, d -> a + d.amount } -
            allExp.filter { it.month <= m }.fold(DualAmount.ZERO) { a, e -> a + e.amount }

    // Opening = exactly last month's closing (what the box started this month with).
    val opening = balanceAsOf(shiftMonth(month, -1))
    val closing = balanceAsOf(month)
    val net = closing - opening

    return ReportData(
        buildingName = building?.name ?: "Residence",
        address = building?.address ?: "",
        month = month,
        collected = collected,
        expected = expected,
        outstanding = expected - collected,
        paidCount = months.count { it.second.status == PaymentStatus.PAID },
        total = apts.size,
        expenses = expensesByCat,
        totalSpent = totalSpent,
        paidList = paidList,
        unpaid = unpaid,
        opening = opening,
        net = net,
        closing = closing,
    )
}

private fun dual(d: DualAmount): String = buildList {
    if (d.usdCents != 0L) add(formatUsd(d.usdCents))
    if (d.lbp != 0L) add(formatLbp(d.lbp))
    if (isEmpty()) add("$0")
}.joinToString(" + ")

/** Plain-text statement for WhatsApp / clipboard / email. */
fun reportToText(r: ReportData): String {
    val lines = mutableListOf(
        "🏢 ${r.buildingName} — ${formatMonth(r.month)}",
        r.address,
        "",
        "Collected: ${dual(r.collected)}",
        "Expected:  ${dual(r.expected)}",
        "Outstanding: ${dual(r.outstanding)}",
        "Spent: ${dual(r.totalSpent)}",
        "Paid: ${r.paidCount}/${r.total} units",
        "",
        "Opening balance: ${dual(r.opening)}",
        "This month (${if (r.isGain) "gain" else "loss"}): ${dual(r.net)}",
        "Closing balance: ${dual(r.closing)}",
    )
    if (r.paidList.isNotEmpty()) {
        lines += ""; lines += "Paid:"
        r.paidList.forEach { lines += "✓ ${it.name} — ${it.owner}: ${dual(it.amount)}${if (it.partial) " (partial)" else ""}" }
    }
    if (r.unpaid.isNotEmpty()) {
        lines += ""; lines += "Still due:"
        r.unpaid.forEach { lines += "• ${it.first} — ${it.second}" }
    }
    lines += ""; lines += "— Sent from BuildingBox"
    return lines.joinToString("\n")
}
