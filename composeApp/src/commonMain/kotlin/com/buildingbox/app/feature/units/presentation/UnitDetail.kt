package com.buildingbox.app.feature.units.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.Avatar
import com.buildingbox.app.core.designsystem.DualMoney
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.SegmentedControl
import com.buildingbox.app.core.designsystem.StatusPill
import com.buildingbox.app.feature.payments.domain.ApartmentMonth
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.domain.aggregate
import com.buildingbox.app.feature.payments.presentation.DueEditor
import com.buildingbox.app.feature.payments.presentation.label
import com.buildingbox.app.feature.payments.presentation.tone
import com.buildingbox.app.feature.units.domain.Apartment
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun UnitDetail(
    apartment: Apartment,
    isAdmin: Boolean,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val duesRepo = koinInject<DuesRepository>()
    val scope = rememberCoroutineScope()

    var rangeMonths by remember { mutableStateOf(12) }
    val allDues by remember(apartment.id) {
        duesRepo.observeAll().map { all -> all.filter { it.apartmentId == apartment.id } }
    }.collectAsStateWithLifecycle(emptyList())
    val months = remember(rangeMonths) { (0 until rangeMonths).map { shiftMonth(currentMonth(), -it) } }
    val history = remember(allDues, months) { months.map { m -> aggregate(apartment.id, m, allDues.filter { it.month == m }) } }
    val currentStatus = history.firstOrNull { it.month == currentMonth() }?.status

    // (month, due|null) → editing existing or adding new
    var dueTarget by remember { mutableStateOf<Pair<String, Due?>?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(apartment.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (isAdmin) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit unit") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(apartment.ownerName, 64.dp)
                Text(apartment.ownerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "${apartment.name} · " + if (apartment.floor == 0) "Ground floor" else "Floor ${apartment.floor}",
                    color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
                )
                if (currentStatus != null) {
                    Spacer(Modifier.size(10.dp))
                    StatusPill(currentStatus.label(), currentStatus.tone())
                }
            }
        }
        if (apartment.phone.isNotBlank()) {
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, null, tint = c.textSecondary)
                        Text(apartment.phone, modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Medium)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(apartment.phone)) }) { Text("Copy") }
                    }
                }
            }
        }
        item {
            Text("Payment history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SegmentedControl(
                    options = listOf(6 to "6 mo", 12 to "1 year", 24 to "2 years"),
                    selected = rangeMonths,
                    onSelect = { rangeMonths = it },
                )
            }
        }
        items(history, key = { it.month }) { am ->
            MonthHistory(am, isAdmin, onEditDue = { dueTarget = am.month to it }, onAddDue = { dueTarget = am.month to null })
        }
    }

    dueTarget?.let { (month, due) ->
        DueEditor(
            initial = due,
            onDismiss = { dueTarget = null },
            onSave = { input ->
                scope.launch { if (due != null) duesRepo.updateDue(due, input) else duesRepo.addDue(apartment.id, month, input) }
                dueTarget = null
            },
            onDelete = if (due != null && !due.base) ({ scope.launch { duesRepo.removeDue(due) }; dueTarget = null }) else null,
        )
    }
}

@Composable
private fun MonthHistory(am: ApartmentMonth, isAdmin: Boolean, onEditDue: (Due) -> Unit, onAddDue: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMonth(am.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusPill(am.status.label(), am.status.tone())
        }
        AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column {
                if (am.dues.isEmpty()) {
                    Text("No dues this month.", color = c.textTertiary, style = MaterialTheme.typography.bodySmall)
                }
                am.dues.forEach { due ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .then(if (isAdmin) Modifier.clickable { onEditDue(due) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PaidMark(due.paid)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(due.title, fontWeight = FontWeight.SemiBold)
                                if (due.base) Text("BASE", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                            }
                            DualMoney(due.amount, compact = true, style = MaterialTheme.typography.bodySmall, weight = FontWeight.Medium)
                        }
                        Text(if (due.paid) "Paid" else "Unpaid", color = if (due.paid) c.flowIn else c.flowOut, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (isAdmin) {
                    TextButton(onClick = onAddDue, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text("Add a due")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Remaining to pay", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            DualMoney(am.remaining, compact = true, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PaidMark(paid: Boolean) {
    val c = LocalAppColors.current
    if (paid) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c.flowIn), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(2.dp, c.hairline, RoundedCornerShape(7.dp)))
    }
}
