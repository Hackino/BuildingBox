package com.buildingbox.app.feature.units.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.Avatar
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.StatusPill
import com.buildingbox.app.core.designsystem.TopBar
import com.buildingbox.app.core.designsystem.dualString
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import com.buildingbox.app.feature.payments.presentation.label
import com.buildingbox.app.feature.payments.presentation.tone
import com.buildingbox.app.feature.units.domain.Apartment
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UnitsScreen(
    isAdmin: Boolean,
    expanded: Boolean,
    openId: String?,
    onOpenChange: (String?) -> Unit,
    viewModel: UnitsViewModel = koinViewModel(),
) {
    val apartments by viewModel.apartments.collectAsStateWithLifecycle()
    val statusMap by viewModel.statusByApartment.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editApt by remember { mutableStateOf<Apartment?>(null) }

    val openApt = apartments.firstOrNull { it.id == openId }

    val list: @Composable () -> Unit = {
        UnitList(apartments, statusMap, isAdmin, openId, onSelect = onOpenChange, onAdd = { showAdd = true })
    }
    val detail: @Composable () -> Unit = {
        if (openApt != null) {
            UnitDetail(openApt, isAdmin, onBack = if (expanded) null else ({ onOpenChange(null) }), onEdit = { editApt = openApt })
        } else {
            Box(Modifier.fillMaxSize()) { Text("Select a unit", color = LocalAppColors.current.textTertiary, modifier = Modifier.align(Alignment.Center)) }
        }
    }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxSize()) { list() }
            Box(Modifier.weight(0.58f).fillMaxSize()) { detail() }
        }
    } else {
        if (openApt != null) detail() else list()
    }

    if (showAdd) {
        ApartmentEditor(initial = null, onDismiss = { showAdd = false }, onSubmit = { viewModel.add(it); showAdd = false })
    }
    editApt?.let { apt ->
        ApartmentEditor(initial = apt, onDismiss = { editApt = null }, onSubmit = { viewModel.update(apt.id, it); editApt = null })
    }
}

@Composable
private fun UnitList(
    apartments: List<Apartment>,
    statusMap: Map<String, PaymentStatus>,
    isAdmin: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Units",
            eyebrow = "The building",
            actions = { if (isAdmin) IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add apartment") } },
        )
        if (apartments.isEmpty()) {
            Box(Modifier.fillMaxSize()) { Text("No apartments yet.", color = c.textTertiary, modifier = Modifier.align(Alignment.Center)) }
            return@Column
        }
        val byFloor = apartments.groupBy { it.floor }.toSortedMap(compareByDescending { it })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            byFloor.forEach { (floor, list) ->
                item(key = "floor-$floor") {
                    Text(
                        if (floor == 0) "Ground floor · shops" else "Floor $floor",
                        style = MaterialTheme.typography.labelMedium, color = c.textTertiary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(list, key = { it.id }) { apt ->
                    UnitRow(apt, statusMap[apt.id] ?: PaymentStatus.UNPAID, selected = apt.id == selectedId, onClick = { onSelect(apt.id) })
                }
            }
        }
    }
}

@Composable
private fun UnitRow(apartment: Apartment, status: PaymentStatus, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(apartment.ownerName, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(apartment.ownerName, fontWeight = FontWeight.SemiBold)
                Text("${apartment.name} · ${dualString(apartment.fee)}/mo", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            StatusPill(status.label(), status.tone())
        }
    }
}
