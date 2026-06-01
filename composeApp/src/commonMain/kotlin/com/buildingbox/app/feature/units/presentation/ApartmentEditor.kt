package com.buildingbox.app.feature.units.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentInput
import com.buildingbox.app.feature.units.domain.floorLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApartmentEditor(
    initial: Apartment?,
    onDismiss: () -> Unit,
    onSubmit: (ApartmentInput) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalAppColors.current

    var owner by remember { mutableStateOf(initial?.ownerName ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var floor by remember { mutableStateOf(initial?.floor ?: 1) }
    var usd by remember { mutableStateOf(initial?.let { (it.fee.usdCents / 100.0).toString() } ?: "15") }
    var lbp by remember { mutableStateOf((initial?.fee?.lbp ?: 1_500_000).toString()) }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }

    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Has the user changed anything from the initial values?
    fun isDirty(): Boolean {
        val i = initial
        return if (i == null) {
            owner.isNotBlank() || name.isNotBlank() || phone.isNotBlank()
        } else {
            owner != i.ownerName || name != i.name || floor != i.floor ||
                usd != (i.fee.usdCents / 100.0).toString() ||
                lbp != i.fee.lbp.toString() || phone != i.phone
        }
    }

    fun attemptDismiss() {
        if (isDirty()) confirmClose = true else onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::attemptDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial == null) "Add apartment" else "Edit apartment",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = colors.flowOut, modifier = Modifier.size(18.dp))
                        Text("Delete", color = colors.flowOut, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Field("Owner name", owner, { owner = it })
            Field("Apartment", name, { name = it })

            // Floor stepper — supports underground levels (negative = basement).
            Text("Floor", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { floor -= 1 }) { Icon(Icons.Filled.Remove, "Lower floor") }
                Text(
                    floorLabel(floor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(onClick = { floor += 1 }) { Icon(Icons.Filled.Add, "Higher floor") }
            }

            Field("Monthly fee (USD)", usd, { usd = it }, KeyboardType.Decimal)
            Field("Monthly fee (LBP)", lbp, { lbp = it.filter(Char::isDigit) }, KeyboardType.Number)
            if (initial != null) {
                Text(
                    "Fee changes apply starting next month — this and past months stay as they are.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            Field("Phone (optional)", phone, { phone = it }, KeyboardType.Phone)

            Button(
                onClick = {
                    if (owner.isBlank() || name.isBlank()) return@Button
                    onSubmit(
                        ApartmentInput(
                            name = name.trim(),
                            ownerName = owner.trim(),
                            floor = floor,
                            feeUsdCents = ((usd.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                            feeLbp = lbp.toLongOrNull() ?: 0,
                            phone = phone.trim(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 16.dp),
            ) {
                Text(if (initial == null) "Add apartment" else "Save changes", fontWeight = FontWeight.SemiBold)
            }
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
            title = { Text("Delete apartment?") },
            text = { Text("This permanently deletes ${initial?.name ?: "this unit"} and all of its dues across every month. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = colors.flowOut, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}
