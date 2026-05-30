package com.buildingbox.app.feature.payments.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DueInput

/** Add/edit a single due (title, dual amount, paid). Used from Unit-Detail history. */
@Composable
fun DueEditor(
    initial: Due?,
    onDismiss: () -> Unit,
    onSave: (DueInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var usd by remember { mutableStateOf(initial?.let { (it.amount.usdCents / 100.0).toString() } ?: "0") }
    var lbp by remember { mutableStateOf((initial?.amount?.lbp ?: 0L).toString()) }
    var paid by remember { mutableStateOf(initial?.paid ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add due" else "Edit due") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, enabled = initial?.base != true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = usd, onValueChange = { usd = it }, label = { Text("Amount (USD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = lbp, onValueChange = { lbp = it.filter(Char::isDigit) }, label = { Text("Amount (LBP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Paid", modifier = Modifier.weight(1f))
                    Switch(checked = paid, onCheckedChange = { paid = it })
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
                        paidOn = null,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
