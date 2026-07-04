package com.buildingbox.app.feature.payments.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.designsystem.AppButton
import com.buildingbox.app.core.designsystem.LoadingOverlay
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.Avatar
import com.buildingbox.app.core.designsystem.DualMoney
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.PillTone
import com.buildingbox.app.core.designsystem.SegmentedControl
import com.buildingbox.app.core.designsystem.StatusPill
import com.buildingbox.app.core.designsystem.TopBar
import com.buildingbox.app.core.designsystem.dualString
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.presentation.AddExpenseSheet
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PaymentsScreen(
    isAdmin: Boolean,
    @Suppress("UNUSED_PARAMETER") expanded: Boolean,
    onOpenUnit: (String) -> Unit,
    viewModel: PaymentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf("month") }
    var editExpense by remember { mutableStateOf<Expense?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Payments", eyebrow = "Subscriptions")
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::prevMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
            Text(formatMonth(state.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
        }

        SegmentedControl(
            options = listOf("month" to "By month", "day" to "By day"),
            selected = view,
            onSelect = { view = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (view == "month") {
            SummaryCard(state)
            if (isAdmin && state.missingBaseDues && state.total > 0) {
                AppButton("Generate ${formatMonth(state.month)} dues", onClick = viewModel::generateBaseDues, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                items(state.rows, key = { it.apartment.id }) { row ->
                    PaymentRowItem(row, onClick = { onOpenUnit(row.apartment.id) })
                }
            }
        } else {
            ByDayView(
                state = state,
                isAdmin = isAdmin,
                onOpenUnit = onOpenUnit,
                onEditExpense = { editExpense = it },
            )
        }
    }
        LoadingOverlay(visible = loading)
    }

    editExpense?.let { e ->
        AddExpenseSheet(
            month = e.month,
            initial = e,
            onDismiss = { editExpense = null },
            onSubmit = { viewModel.updateExpense(e.month, e.id, it); editExpense = null },
            onDelete = if (isAdmin) ({ viewModel.deleteExpense(e.month, e.id); editExpense = null }) else null,
        )
    }
}

@Composable
private fun SummaryCard(state: PaymentsUiState) {
    val c = LocalAppColors.current
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("${state.paidCount}/${state.total}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("units fully paid", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(8.dp))
            Pot("USD collected", "${formatUsd(state.collected.usdCents)} / ${formatUsd(state.expected.usdCents)}")
            Pot("LBP collected", "${formatLbp(state.collected.lbp, true)} / ${formatLbp(state.expected.lbp, true)}")
        }
    }
}

@Composable
private fun Pot(label: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.textTertiary, style = MaterialTheme.typography.labelMedium)
        Text(value, fontFamily = com.buildingbox.app.core.designsystem.LocalAppFonts.current.mono, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PaymentRowItem(row: PaymentRow, onClick: () -> Unit) {
    val c = LocalAppColors.current
    // Persisted across scroll + recomposition, keyed by apartment so each row
    // remembers its own expanded state.
    var expanded by rememberSaveable(row.apartment.id) { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tap surface for navigation: the whole "avatar + name column" block —
                // NEVER the trailing chevron. Keeps expansion strictly opt-in.
                Row(
                    Modifier.weight(1f).clickable(onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(row.apartment.ownerName, 42.dp)
                    Column(Modifier.weight(1f)) {
                        Text(row.apartment.ownerName, fontWeight = FontWeight.SemiBold)
                        Text("${row.apartment.name} · total ${dualString(row.month.total)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        if (row.month.status == PaymentStatus.PARTIAL) {
                            Text("${dualString(row.month.remaining)} left", style = MaterialTheme.typography.labelSmall, color = c.flowOut, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                StatusPill(row.month.status.label(), row.month.status.tone())
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                UnitBreakdown(row)
            }
        }
    }
}

/**
 * The expandable body under each unit row on the Payments tab. Shows per-currency
 * paid/total/remaining plus a compact per-due list. Read-only — editing still
 * happens via Unit Detail.
 */
@Composable
private fun UnitBreakdown(row: PaymentRow) {
    val c = LocalAppColors.current
    val m = row.month
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        // Thin separator to visually detach the breakdown from the row header.
        Box(Modifier.fillMaxWidth().size(1.dp).padding(bottom = 8.dp))

        if (m.total.hasUsd) BreakdownLine("USD", formatUsd(m.paid.usdCents), formatUsd(m.total.usdCents), formatUsd((m.total - m.paid).usdCents), m.paid.usdCents >= m.total.usdCents)
        if (m.total.hasLbp) BreakdownLine("LBP", formatLbp(m.paid.lbp, true), formatLbp(m.total.lbp, true), formatLbp((m.total - m.paid).lbp, true), m.paid.lbp >= m.total.lbp)

        if (m.dues.isNotEmpty()) {
            Text(
                "Dues".uppercase(),
                color = c.textTertiary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            m.dues.forEach { d -> DueLine(d) }
        }
    }
}

@Composable
private fun BreakdownLine(currency: String, paid: String, total: String, remaining: String, fully: Boolean) {
    val c = LocalAppColors.current
    val mono = com.buildingbox.app.core.designsystem.LocalAppFonts.current.mono
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(currency, color = c.textTertiary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("paid $paid / $total", fontFamily = mono, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                if (fully) "fully paid" else "remaining $remaining",
                color = if (fully) c.flowIn else c.flowOut,
                fontFamily = if (fully) null else mono,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DueLine(d: Due) {
    val c = LocalAppColors.current
    val partial = !d.isFullyPaid && !d.isUntouched
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                "${dualString(d.paidAmount)} / ${dualString(d.amount)}",
                color = c.textTertiary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val (label, tone) = when {
            d.isFullyPaid -> "Paid" to c.flowIn
            partial -> "Partial · ${dualString(d.remaining)} left" to c.warn
            else -> "Unpaid" to c.flowOut
        }
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ByDayView(
    state: PaymentsUiState,
    isAdmin: Boolean,
    onOpenUnit: (String) -> Unit,
    onEditExpense: (Expense) -> Unit,
) {
    val c = LocalAppColors.current
    // Paid dues this month grouped by paid date (carry apartmentId so a row links to its
    // unit, and the paid date so each item can show it).
    data class Paid(val apartmentId: String, val owner: String, val title: String, val date: String, val amount: com.buildingbox.app.core.money.DualAmount)
    val byDay = remember(state) {
        // Include partial dues too — they carry a paidOn once anything is paid.
        // The row amount shows the paidAmount (not total) so a $200/$500 partial
        // renders as "+$200" on the day it was paid.
        state.rows.flatMap { r ->
            r.month.dues.filter { !it.isUntouched }.map { d ->
                val on = d.paidOn ?: today()
                on to Paid(r.apartment.id, r.apartment.ownerName, d.title, on, d.paidAmount)
            }
        }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it })
    }
    if (byDay.isEmpty() && state.expenses.isEmpty()) {
        Text("Nothing recorded this month.", color = c.textTertiary, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        byDay.forEach { (date, list) ->
            item(key = "day-$date") {
                Text(date, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(list) { p ->
                AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenUnit(p.apartmentId) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.owner, fontWeight = FontWeight.SemiBold)
                            Text("${p.title} · ${formatDayLong(p.date)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        DualMoney(p.amount, compact = true, sign = "+", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (state.expenses.isNotEmpty()) {
            item(key = "expenses-header") {
                Text("Expenses · ${formatMonth(state.month)}", style = MaterialTheme.typography.labelMedium, color = c.textTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
            items(state.expenses, key = { "exp-${it.id}" }) { e ->
                val mod = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .then(if (isAdmin) Modifier.clickable { onEditExpense(e) } else Modifier)
                AppCard(mod) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, fontWeight = FontWeight.SemiBold)
                            Text("${e.category.label} · ${e.date}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        DualMoney(e.amount, compact = true, sign = "−", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

fun PaymentStatus.label(): String = when (this) {
    PaymentStatus.NONE -> "No dues"
    PaymentStatus.PAID -> "Paid"
    PaymentStatus.PARTIAL -> "Partial"
    PaymentStatus.UNPAID -> "Unpaid"
}

fun PaymentStatus.tone(): PillTone = when (this) {
    PaymentStatus.NONE -> PillTone.NEUTRAL
    PaymentStatus.PAID -> PillTone.POSITIVE
    PaymentStatus.PARTIAL -> PillTone.WARNING
    PaymentStatus.UNPAID -> PillTone.NEGATIVE
}
