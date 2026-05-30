package com.buildingbox.app.feature.payments.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.designsystem.AppButton
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
    var view by remember { mutableStateOf("month") }

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
            ByDayView(state)
        }
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
    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(row.apartment.ownerName, 42.dp)
            Column(Modifier.weight(1f)) {
                Text(row.apartment.ownerName, fontWeight = FontWeight.SemiBold)
                Text("${row.apartment.name} · total ${dualString(row.month.total)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                if (row.month.status == PaymentStatus.PARTIAL) {
                    Text("${dualString(row.month.remaining)} left", style = MaterialTheme.typography.labelSmall, color = c.flowOut, fontWeight = FontWeight.Bold)
                }
            }
            StatusPill(row.month.status.label(), row.month.status.tone())
        }
    }
}

@Composable
private fun ByDayView(state: PaymentsUiState) {
    val c = LocalAppColors.current
    // Paid dues this month grouped by paid date.
    data class Paid(val owner: String, val title: String, val amount: com.buildingbox.app.core.money.DualAmount)
    val byDay = remember(state) {
        state.rows.flatMap { r -> r.month.dues.filter { it.paid }.map { d -> (d.paidOn ?: today()) to Paid(r.apartment.ownerName, d.title, d.amount) } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it })
    }
    if (byDay.isEmpty()) {
        Text("No payments recorded this month.", color = c.textTertiary, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        byDay.forEach { (date, list) ->
            item(key = "day-$date") {
                Text(date, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(list) { p ->
                AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.owner, fontWeight = FontWeight.SemiBold)
                            Text(p.title, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        DualMoney(p.amount, compact = true, sign = "+", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

fun PaymentStatus.label(): String = when (this) {
    PaymentStatus.PAID -> "Paid"
    PaymentStatus.PARTIAL -> "Partial"
    PaymentStatus.UNPAID -> "Unpaid"
}

fun PaymentStatus.tone(): PillTone = when (this) {
    PaymentStatus.PAID -> PillTone.POSITIVE
    PaymentStatus.PARTIAL -> PillTone.WARNING
    PaymentStatus.UNPAID -> PillTone.NEGATIVE
}
