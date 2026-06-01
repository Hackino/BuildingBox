package com.buildingbox.app.feature.calendar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.ExpenseCategory
import com.buildingbox.app.feature.calendar.domain.ExpenseInput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    month: String,
    onDismiss: () -> Unit,
    onSubmit: (ExpenseInput) -> Unit,
    initial: Expense? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editing = initial != null
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: ExpenseCategory.UTILITIES) }
    var usd by remember { mutableStateOf(initial?.let { (it.amount.usdCents / 100.0).toString() } ?: "0") }
    var lbp by remember { mutableStateOf((initial?.amount?.lbp ?: 0L).toString()) }
    var date by remember { mutableStateOf(initial?.date ?: if (month == currentMonth()) today() else dateOf(month, 1)) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    if (editing) "Edit expense" else "Add expense",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = LocalAppColors.current.flowOut, modifier = Modifier.size(18.dp))
                        Text("Delete", color = LocalAppColors.current.flowOut, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            OutlinedTextField(label, { label = it }, label = { Text("What was it for?") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) })
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(usd, { usd = it }, label = { Text("USD") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(lbp, { lbp = it.filter(Char::isDigit) }, label = { Text("LBP") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }

            // Read-only field that opens a date picker — avoids typo'd / wrong-format dates.
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = formatDayLong(date),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, "Pick date") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Transparent overlay catches the tap (OutlinedTextField readOnly still
                // swallows clicks otherwise).
                Box(Modifier.matchParentSize().clickable { showPicker = true })
            }

            if (showPicker) {
                val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(date))
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { date = millisToDate(it) }
                            showPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
                ) {
                    DatePicker(state = pickerState)
                }
            }

            Button(
                onClick = {
                    if (label.isBlank()) return@Button
                    onSubmit(
                        ExpenseInput(
                            label = label.trim(),
                            category = category,
                            date = date,
                            usdCents = ((usd.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                            lbp = lbp.toLongOrNull() ?: 0,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 16.dp),
            ) { Text(if (editing) "Save changes" else "Add expense", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete expense?") },
            text = { Text("This permanently removes \"${initial?.label ?: "this expense"}\". This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = LocalAppColors.current.flowOut, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// --- date ⇄ millis helpers for the Material date picker (UTC, date-only) ---

/** "YYYY-MM-DD" → UTC start-of-day epoch millis (what DatePicker expects). */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun dateToMillis(date: String): Long =
    runCatching {
        LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }.getOrElse {
        LocalDate.parse(today()).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }

/** Epoch millis (UTC, from DatePicker) → "YYYY-MM-DD". */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun millisToDate(millis: Long): String {
    val d = kotlin.time.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}
