package com.buildingbox.app.feature.payments.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.buildingbox.app.core.designsystem.dualString
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DueInput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Add/edit a single due — captures the expected **total** (USD + LBP) and how much
 * of it has been **paid so far** (USD + LBP), plus the payment date once anything
 * is paid. Save is blocked when paid > total in either currency to prevent
 * negative remaining values leaking into Reports / Payments / Dashboard.
 */
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
    // Paid-so-far mirrors the total row. Legacy records with `paid=true` but no
    // paid* fields already surface as `paidAmount = amount` via toDomain(), so
    // this prefill naturally covers both new and legacy shapes.
    var paidUsd by remember { mutableStateOf(initial?.let { (it.paidAmount.usdCents / 100.0).toString() } ?: "0") }
    var paidLbpStr by remember { mutableStateOf((initial?.paidAmount?.lbp ?: 0L).toString()) }
    // Pre-fill from the existing pay date so editing never overwrites it; defaults
    // to today only when the user first crosses zero on the paid row.
    var paidOn by remember { mutableStateOf(initial?.paidOn ?: today()) }

    var showPicker by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Parsed values — recomputed each recomposition; cheap.
    val totalUsdCents = ((usd.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val totalLbp = lbp.toLongOrNull() ?: 0L
    val paidUsdCents = ((paidUsd.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val paidLbp = paidLbpStr.toLongOrNull() ?: 0L
    val total = DualAmount(totalUsdCents, totalLbp)
    val paidAmount = DualAmount(paidUsdCents, paidLbp)
    val touched = paidUsdCents > 0L || paidLbp > 0L
    val overpaid = paidUsdCents > totalUsdCents || paidLbp > totalLbp
    val remaining = total - paidAmount
    val fullyPaid = touched && !overpaid && paidUsdCents == totalUsdCents && paidLbp == totalLbp

    fun isDirty(): Boolean {
        val i = initial
        return if (i == null) {
            title.isNotBlank() || usd != "0" || lbp != "0" || paidUsd != "0" || paidLbpStr != "0"
        } else {
            title != i.title ||
                usd != (i.amount.usdCents / 100.0).toString() ||
                lbp != i.amount.lbp.toString() ||
                paidUsd != (i.paidAmount.usdCents / 100.0).toString() ||
                paidLbpStr != i.paidAmount.lbp.toString() ||
                (touched && paidOn != (i.paidOn ?: today()))
        }
    }

    fun attemptDismiss() {
        if (isDirty()) confirmClose = true else onDismiss()
    }

    val canSave = title.isNotBlank() && !overpaid

    AlertDialog(
        onDismissRequest = ::attemptDismiss,
        title = {
            Column {
                Text(if (initial == null) "Add due" else "Edit due")
                // The month/year this due belongs to (its RTDB shard). Read-only.
                Text(formatMonth(month), style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
        },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, enabled = initial?.base != true, modifier = Modifier.fillMaxWidth())

                // — Expected total —
                SectionLabel("Total expected", modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(value = usd, onValueChange = { usd = it }, label = { Text("Total (USD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                OutlinedTextField(value = lbp, onValueChange = { lbp = it.filter(Char::isDigit) }, label = { Text("Total (LBP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                // — Paid so far —
                SectionLabel("Paid so far", modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(value = paidUsd, onValueChange = { paidUsd = it }, label = { Text("Paid (USD)") }, singleLine = true, isError = paidUsdCents > totalUsdCents, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                OutlinedTextField(value = paidLbpStr, onValueChange = { paidLbpStr = it.filter(Char::isDigit) }, label = { Text("Paid (LBP)") }, singleLine = true, isError = paidLbp > totalLbp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                // — Live status hint below the paid row —
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    when {
                        overpaid -> Text(
                            "Paid cannot exceed total",
                            color = c.flowOut,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        fullyPaid -> Text(
                            "Fully paid ✓",
                            color = c.flowIn,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        touched -> Text(
                            "Remaining ${dualString(remaining)}",
                            color = c.warn,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        else -> Text(
                            "Not paid yet",
                            color = c.textTertiary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Payment-date picker — only relevant once any amount has been paid.
                if (touched) {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
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
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        DueInput(
                            title = title.trim(),
                            usdCents = totalUsdCents,
                            lbp = totalLbp,
                            paidUsdCents = paidUsdCents,
                            paidLbp = paidLbp,
                            paidOn = if (touched) paidOn else null,
                        ),
                    )
                },
            ) { Text("Save") }
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

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Text(
        text.uppercase(),
        color = c.textTertiary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
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
