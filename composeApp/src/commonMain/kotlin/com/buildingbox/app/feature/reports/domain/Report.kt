package com.buildingbox.app.feature.reports.domain

import com.buildingbox.app.core.datetime.formatDayLong
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
import com.buildingbox.app.feature.units.domain.floorLabel
import kotlinx.serialization.Serializable

/** /building meta. */
@Serializable
data class BuildingDto(
    val name: String = "Residence",
    val address: String = "",
    val currentMonth: String = "",
)

data class PaidEntry(
    val name: String,
    val owner: String,
    val floor: Int,
    val amount: DualAmount,
    val date: String?,
)

/**
 * A unit that still owes money this month — pure unpaid or partially paid.
 * [partial] units carry non-zero [paid] and a positive [remaining].
 */
data class UnpaidEntry(
    val name: String,
    val owner: String,
    val floor: Int,
    val paid: DualAmount,
    val remaining: DualAmount,
    val partial: Boolean,
)

/** One expense line in the report — carries its reason ([label]) plus category and date. */
data class ExpenseLine(val label: String, val category: ExpenseCategory, val amount: DualAmount, val date: String)

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
    /** Individual expense lines (with their reason text), newest first. */
    val expenseItems: List<ExpenseLine>,
    val totalSpent: DualAmount,
    val paidList: List<PaidEntry>,
    val unpaid: List<UnpaidEntry>,
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

    val monthExpenses = allExp.filter { it.month == month }
    val expensesByCat = monthExpenses
        .groupBy { it.category }
        .map { (cat, list) -> cat to list.fold(DualAmount.ZERO) { a, e -> a + e.amount } }
        .sortedByDescending { it.second.usdCents + it.second.lbp / 1_000_000 }
    val expenseItems = monthExpenses
        .sortedByDescending { it.date }
        .map { ExpenseLine(it.label, it.category, it.amount, it.date) }
    val totalSpent = expensesByCat.fold(DualAmount.ZERO) { a, (_, amt) -> a + amt }

    // "Paid"/"Still due" lists only concern apartments that actually have dues this
    // month — a unit with no dues (status NONE) belongs in neither list.
    // A PARTIAL apartment appears only in Still-due (with `remaining` shown), never Paid.
    val withDues = months.filter { it.second.status != PaymentStatus.NONE }
    val paidList = withDues.filter { it.second.status == PaymentStatus.PAID }
        .map { (a, am) ->
            // Most recent pay date among this unit's dues this month.
            val paidOn = am.dues.mapNotNull { it.paidOn }.maxOrNull()
            PaidEntry(
                name = a.name,
                owner = a.ownerName,
                floor = a.floor,
                amount = am.paid,
                date = paidOn,
            )
        }
    val unpaid = withDues.filter { it.second.status != PaymentStatus.PAID }
        .map { (a, am) ->
            UnpaidEntry(
                name = a.name,
                owner = a.ownerName,
                floor = a.floor,
                paid = am.paid,
                remaining = am.remaining,
                partial = am.status == PaymentStatus.PARTIAL,
            )
        }

    // Balance in the box at the end of any month = paid amount (partial-aware) − expenses up to and including it.
    fun balanceAsOf(m: String): DualAmount =
        allDues.filter { it.month <= m }.fold(DualAmount.ZERO) { a, d -> a + d.paidAmount } -
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
        expenseItems = expenseItems,
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
        r.paidList.forEach {
            val on = it.date?.let { d -> ", ${formatDayLong(d)}" } ?: ""
            lines += "✓ ${it.owner} · ${floorLabel(it.floor)} · ${it.name}: ${dual(it.amount)}$on"
        }
    }
    if (r.expenseItems.isNotEmpty()) {
        lines += ""; lines += "Expenses:"
        r.expenseItems.forEach { e ->
            val reason = e.label.ifBlank { e.category.label }
            lines += "• $reason (${e.category.label}, ${formatDayLong(e.date)}): ${dual(e.amount)}"
        }
    }
    if (r.unpaid.isNotEmpty()) {
        lines += ""; lines += "Still due:"
        r.unpaid.forEach {
            val tail = if (it.partial) " (partial · paid ${dual(it.paid)} · remaining ${dual(it.remaining)})"
                else " (remaining ${dual(it.remaining)})"
            lines += "• ${it.name} — ${it.owner} · ${floorLabel(it.floor)}$tail"
        }
    }
    lines += ""; lines += "— Sent from BuildingBox"
    return lines.joinToString("\n")
}
