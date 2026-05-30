package com.buildingbox.app.feature.units.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApartmentEditor(
    initial: Apartment?,
    onDismiss: () -> Unit,
    onSubmit: (ApartmentInput) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalAppColors.current

    var owner by remember { mutableStateOf(initial?.ownerName ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var floor by remember { mutableStateOf((initial?.floor ?: 1).toString()) }
    var usd by remember { mutableStateOf(initial?.let { (it.fee.usdCents / 100.0).toString() } ?: "15") }
    var lbp by remember { mutableStateOf((initial?.fee?.lbp ?: 1_500_000).toString()) }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (initial == null) "Add apartment" else "Edit apartment",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Field("Owner name", owner, { owner = it })
            Field("Apartment", name, { name = it })
            Field("Floor", floor, { floor = it.filter(Char::isDigit) }, KeyboardType.Number)
            Field("Monthly fee (USD)", usd, { usd = it }, KeyboardType.Decimal)
            Field("Monthly fee (LBP)", lbp, { lbp = it.filter(Char::isDigit) }, KeyboardType.Number)
            if (initial != null) {
                Text(
                    "Fee changes apply starting next month — this and past months stay as they are.",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
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
                            floor = floor.toIntOrNull() ?: 0,
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
