package com.buildingbox.app.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.DualMoney
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.LocalAppFonts
import com.buildingbox.app.core.designsystem.ProgressRing
import com.buildingbox.app.core.designsystem.Sparkline
import com.buildingbox.app.core.designsystem.TopBar
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd
import com.buildingbox.app.feature.calendar.domain.Movement
import com.buildingbox.app.feature.calendar.domain.MovementKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    displayName: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenUnit: (String) -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            TopBar(
                title = formatMonth(state.month),
                eyebrow = "Hello, ${displayName.ifBlank { "Syndic" }}",
                actions = { IconButton(onClick = onToggleTheme) { Icon(if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Toggle theme") } },
            )
        }
        item { BalanceCard(state, Modifier.padding(horizontal = 16.dp)) }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowCard("Collected in", state.inThisMonth, c.flowIn, Icons.Filled.ArrowDownward, Modifier.weight(1f).fillMaxHeight())
                FlowCard("Spent out", state.outThisMonth, c.flowOut, Icons.Filled.ArrowUpward, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item {
            Text("This month's dues", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        }
        item { CollectionCard(state, Modifier.padding(horizontal = 16.dp)) }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        itemsIndexed(state.recent, key = { i, m -> "$i-${m.id}" }) { _, m ->
            // Income rows link to the unit that paid; expense rows aren't navigable here.
            val onClick = m.apartmentId?.let { id -> { onOpenUnit(id) } }
            RecentRow(m, Modifier.padding(horizontal = 16.dp), onClick = onClick)
        }
        item {
            AppCard(Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onOpenReports), inset = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Send monthly report", fontWeight = FontWeight.Bold)
                        Text("Share ${formatMonth(state.month)} with owners", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(state: DashboardUiState, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E7C68), Color(0xFF0A4F45)))),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Building box · available", color = Color(0xFFB9EADF), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Account("USD account", formatUsd(state.balance.usdCents), state.trendUsd, "$")
            Box(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 0.dp).background(Color.White.copy(alpha = 0.15f)))
            Account("LBP account", formatLbp(state.balance.lbp), state.trendLbp, "LL")
        }
    }
}

@Composable
private fun Account(label: String, value: String, trend: List<Float>, unit: String) {
    val mono = LocalAppFonts.current.mono
    val delta = if (trend.size >= 2) trend.last() - trend[trend.size - 2] else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), color = Color(0xFFB9EADF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            if (delta != 0f) {
                val sign = if (delta > 0) "+" else "−"
                val magnitude = kotlin.math.abs(delta).toLong()
                val amt = if (unit == "$") formatUsd(magnitude, compact = true) else formatLbp(magnitude, compact = true)
                Text("$sign$amt", color = Color(0xFFB9EADF), fontFamily = mono, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(value, color = Color.White, fontFamily = mono, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
        Sparkline(trend, Color(0xFF8FE3D2), Modifier.fillMaxWidth().height(28.dp).padding(top = 6.dp))
    }
}

@Composable
private fun FlowCard(label: String, amount: com.buildingbox.app.core.money.DualAmount, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val c = LocalAppColors.current
    AppCard(modifier) {
        Column {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(label, color = c.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            DualMoney(amount, compact = true, style = MaterialTheme.typography.titleMedium, stacked = true)
        }
    }
}

@Composable
private fun CollectionCard(state: DashboardUiState, modifier: Modifier) {
    val c = LocalAppColors.current
    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(
                progress = if (state.total > 0) state.paid.toFloat() / state.total else 0f,
                track = c.surfaceInset, color = c.accent, size = 104.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.paid}/${state.total}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, fontFamily = LocalAppFonts.current.mono)
                    Text("paid", color = c.textTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendRow(c.flowIn, "Paid", state.paid)
                LegendRow(c.warn, "Partial", state.partial)
                LegendRow(c.flowOut, "Unpaid", state.unpaid)
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, color = c.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
        Text(count.toString(), fontWeight = FontWeight.Bold, fontFamily = LocalAppFonts.current.mono, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecentRow(m: Movement, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    val isIn = m.kind == MovementKind.IN
    val color = if (isIn) c.flowIn else c.flowOut
    val rowMod = modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 6.dp)
    Row(rowMod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (isIn) c.flowInSoft else c.flowOutSoft), contentAlignment = Alignment.Center) {
            Icon(if (isIn) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(m.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            val sub = listOfNotNull(m.sublabel, formatDayLong(m.date)).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = c.textTertiary, style = MaterialTheme.typography.labelMedium)
        }
        DualMoney(m.amount, compact = true, sign = if (isIn) "+" else "−", style = MaterialTheme.typography.bodyMedium)
    }
}
