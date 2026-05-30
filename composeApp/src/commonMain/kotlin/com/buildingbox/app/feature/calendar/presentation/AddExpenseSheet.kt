package com.buildingbox.app.feature.calendar.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.feature.calendar.domain.ExpenseCategory
import com.buildingbox.app.feature.calendar.domain.ExpenseInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    month: String,
    onDismiss: () -> Unit,
    onSubmit: (ExpenseInput) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.UTILITIES) }
    var usd by remember { mutableStateOf("0") }
    var lbp by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(if (month == currentMonth()) today() else dateOf(month, 1)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Add expense", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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

            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

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
            ) { Text("Add expense", fontWeight = FontWeight.SemiBold) }
        }
    }
}
