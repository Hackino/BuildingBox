package com.buildingbox.app.feature.payments.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DueInput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Add/edit a single due (title, dual amount, paid, paid-date). Used from Unit-Detail history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueEditor(
    initial: Due?,
    month: String,
    onDismiss: () -> Unit,
    onSave: (DueInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = LocalAppColors.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var usd by remember { mutableStateOf(initial?.let { (it.amount.usdCents / 100.0).toString() } ?: "0") }
    var lbp by remember { mutableStateOf((initial?.amount?.lbp ?: 0L).toString()) }
    var paid by remember { mutableStateOf(initial?.paid ?: false) }
    // Prefilled from the existing pay date so editing never overwrites it; defaults
    // to today only for a freshly-marked-paid due that has none yet.
    var paidOn by remember { mutableStateOf(initial?.paidOn ?: today()) }

    var showPicker by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun isDirty(): Boolean {
        val i = initial
        return if (i == null) {
            title.isNotBlank() || usd != "0" || lbp != "0" || paid
        } else {
            title != i.title ||
                usd != (i.amount.usdCents / 100.0).toString() ||
                lbp != i.amount.lbp.toString() || paid != i.paid ||
                (paid && paidOn != (i.paidOn ?: today()))
        }
    }

    fun attemptDismiss() {
        if (isDirty()) confirmClose = true else onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::attemptDismiss,
        title = {
            Column {
                Text(if (initial == null) "Add due" else "Edit due")
                // The month/year this due belongs to (its RTDB shard). Read-only.
                Text(formatMonth(month), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
        },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, enabled = initial?.base != true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = usd, onValueChange = { usd = it }, label = { Text("Amount (USD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = lbp, onValueChange = { lbp = it.filter(Char::isDigit) }, label = { Text("Amount (LBP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Paid", modifier = Modifier.weight(1f))
                    Switch(checked = paid, onCheckedChange = { paid = it })
                }
                // Payment-date picker — only relevant (and shown) when the due is paid.
                if (paid) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = formatDayLong(paidOn),
                            onValueChange = {},
                            label = { Text("Payment date") },
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { Icon(Icons.Filled.CalendarMonth, "Pick date") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.matchParentSize().clickable { showPicker = true })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                onSave(
                    DueInput(
                        title = title.trim(),
                        usdCents = ((usd.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                        lbp = lbp.toLongOrNull() ?: 0,
                        paid = paid,
                        paidOn = if (paid) paidOn else null,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = c.flowOut) }
                TextButton(onClick = ::attemptDismiss) { Text("Cancel") }
            }
        },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(paidOn))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { paidOn = millisToDate(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Close without saving?") },
            confirmButton = { TextButton(onClick = { confirmClose = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this due?") },
            text = { Text("This permanently removes \"${initial?.title ?: "this due"}\". This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = c.flowOut, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// --- date ⇄ millis helpers for the Material date picker (UTC, date-only) ---

/** "YYYY-MM-DD" → UTC start-of-day epoch millis. Falls back to today on a bad value. */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun dateToMillis(date: String): Long =
    runCatching { LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
        .getOrElse { LocalDate.parse(today()).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }

/** Epoch millis (UTC, from DatePicker) → "YYYY-MM-DD". */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun millisToDate(millis: Long): String {
    val d = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}
