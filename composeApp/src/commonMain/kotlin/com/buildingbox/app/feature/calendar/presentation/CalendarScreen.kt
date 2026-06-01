package com.buildingbox.app.feature.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
import com.buildingbox.app.core.datetime.daysInMonth
import com.buildingbox.app.core.datetime.firstWeekdayIndex
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.LoadingOverlay
import com.buildingbox.app.core.designsystem.DualMoney
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.SegmentedControl
import com.buildingbox.app.core.designsystem.TopBar
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.Movement
import com.buildingbox.app.feature.calendar.domain.MovementKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    isAdmin: Boolean,
    onOpenUnit: (String) -> Unit = {},
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    var view by remember { mutableStateOf("day") }
    var addExpense by remember { mutableStateOf(false) }
    var editExpense by remember { mutableStateOf<Expense?>(null) }
    var selectedDay by remember(state.month) {
        mutableStateOf(if (state.month == currentMonth()) today().split("-")[2].toIntOrNull() else null)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Calendar",
            eyebrow = "Money box",
            actions = { if (isAdmin) IconButton(onClick = { addExpense = true }) { Icon(Icons.Filled.Add, "Add expense") } },
        )

        // Cap content width so the square day-grid doesn't blow up on wide desktop windows.
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.CenterHorizontally).padding(horizontal = 16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::prevMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
                    Text(formatMonth(state.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowTotal("In", state.inTotal, c.flowIn, "+", Modifier.weight(1f).fillMaxHeight())
                    FlowTotal("Out", state.outTotal, c.flowOut, "−", Modifier.weight(1f).fillMaxHeight())
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    MonthGrid(
                        month = state.month,
                        daysIn = state.daysIn,
                        daysOut = state.daysOut,
                        selectedDay = if (view == "day") selectedDay else null,
                        today = if (state.month == currentMonth()) today().split("-")[2].toIntOrNull() else null,
                        onSelect = { selectedDay = it; view = "day" },
                    )
                }
            }
            item {
                Box(Modifier.padding(top = 12.dp)) {
                    SegmentedControl(
                        options = listOf("day" to "Selected day", "month" to "Whole month"),
                        selected = view, onSelect = { view = it },
                    )
                }
            }

            val shown = if (view == "month") state.movements
            else state.movements.filter { selectedDay != null && it.date == dateOf(state.month, selectedDay!!) }

            item {
                val title = if (view == "month") "All of ${formatMonth(state.month)}"
                else selectedDay?.let { formatDayLong(dateOf(state.month, it)) } ?: "Select a day"
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
            if (shown.isEmpty()) {
                item { Text("Nothing moved on this " + if (view == "month") "month." else "day.", color = c.textTertiary, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            itemsIndexed(shown, key = { i, m -> "$i-${m.id}" }) { _, m ->
                MovementRow(
                    m,
                    showDate = view == "month",
                    // Income → open the unit; expense (admin) → open the edit sheet.
                    onClick = when {
                        m.kind == MovementKind.IN && m.apartmentId != null -> ({ onOpenUnit(m.apartmentId) })
                        m.kind == MovementKind.OUT && isAdmin && m.expenseId != null ->
                            ({ editExpense = state.expenses.firstOrNull { it.id == m.expenseId } })
                        else -> null
                    },
                )
            }
            item { Box(Modifier.size(24.dp)) }
        }
    }
        LoadingOverlay(visible = loading)
    }

    if (addExpense) {
        AddExpenseSheet(
            month = state.month,
            onDismiss = { addExpense = false },
            onSubmit = { viewModel.addExpense(it); addExpense = false },
        )
    }

    editExpense?.let { e ->
        AddExpenseSheet(
            month = e.month,
            initial = e,
            onDismiss = { editExpense = null },
            onSubmit = { viewModel.updateExpense(e.month, e.id, it); editExpense = null },
            onDelete = { viewModel.deleteExpense(e.month, e.id); editExpense = null },
        )
    }
}

@Composable
private fun FlowTotal(label: String, amount: com.buildingbox.app.core.money.DualAmount, color: Color, sign: String, modifier: Modifier) {
    val c = LocalAppColors.current
    AppCard(modifier, inset = true) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f)) {
                Text(label, color = c.textTertiary, style = MaterialTheme.typography.labelMedium)
                DualMoney(amount, compact = true, sign = sign, style = MaterialTheme.typography.bodyMedium, stacked = true)
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: String,
    daysIn: Set<Int>,
    daysOut: Set<Int>,
    selectedDay: Int?,
    today: Int?,
    onSelect: (Int) -> Unit,
) {
    val c = LocalAppColors.current
    val lead = firstWeekdayIndex(month)
    val total = daysInMonth(month)
    val cells = buildList { repeat(lead) { add(null) }; for (d in 1..total) add(d) }
    val weeks = cells.chunked(7)

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), color = c.textTertiary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val selected = day == selectedDay
                            Box(
                                Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) c.accent else Color.Transparent)
                                    .clickable { onSelect(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        day.toString(),
                                        color = if (selected) Color.White else if (day == today) c.accent else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (day == today || selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (day in daysIn) Dot(if (selected) Color.White else c.flowIn)
                                        if (day in daysOut) Dot(if (selected) Color.White else c.flowOut)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun MovementRow(m: Movement, showDate: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    val isIn = m.kind == MovementKind.IN
    val color = if (isIn) c.flowIn else c.flowOut
    // In the whole-month view rows aren't grouped under a day header, so show the date here.
    val sub = listOfNotNull(m.sublabel, if (showDate) formatDayLong(m.date) else null).joinToString(" · ")
    val cardMod = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    AppCard(cardMod) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (isIn) c.flowInSoft else c.flowOutSoft), contentAlignment = Alignment.Center) {
                Icon(if (isIn) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(m.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (sub.isNotEmpty()) Text(sub, color = c.textTertiary, style = MaterialTheme.typography.labelMedium)
            }
            DualMoney(m.amount, compact = true, sign = if (isIn) "+" else "−", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
