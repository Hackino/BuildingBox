package com.buildingbox.app.feature.reports.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.designsystem.AppButton
import com.buildingbox.app.core.designsystem.AppCard
import com.buildingbox.app.core.designsystem.DualMoney
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.TopBar
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.auth.domain.AuthRepository
import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter
import com.buildingbox.app.feature.reports.domain.reportToText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = koinViewModel()) {
    val report by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val authRepo = koinInject<AuthRepository>()
    val exporter = koinInject<ReportExporter>()
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to manage the building.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; scope.launch { authRepo.signOut() } }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Reports",
            eyebrow = "Owners' statement",
            actions = { IconButton(onClick = { confirmSignOut = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::prevMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
            Text(report?.month?.let { formatMonth(it) } ?: "…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
        }

        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize()) { Text("Loading…", color = c.textTertiary, modifier = Modifier.align(Alignment.Center)) }
            return
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item { ReportCard(r) }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    AppButton(
                        "Share PDF",
                        onClick = { exporter.sharePdf(r) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        leading = { Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.height(18.dp)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton(
                            "Download",
                            onClick = { exporter.downloadPdf(r) },
                            ghost = true,
                            modifier = Modifier.weight(1f).height(48.dp),
                            leading = { Icon(Icons.Filled.Download, null, modifier = Modifier.height(18.dp)) },
                        )
                        AppButton(
                            "Copy",
                            onClick = { clipboard.setText(AnnotatedString(reportToText(r))) },
                            ghost = true,
                            modifier = Modifier.weight(1f).height(48.dp),
                            leading = { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.height(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(r: ReportData) {
    val c = LocalAppColors.current
    AppCard(Modifier.fillMaxWidth().padding(top = 8.dp), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column {
            // Header
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E7C68), Color(0xFF0A4F45)))).padding(20.dp),
            ) {
                Text(r.buildingName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("${r.address} · ${formatMonth(r.month)}", color = Color(0xFFB9EADF), style = MaterialTheme.typography.bodySmall)
            }

            Section("Collection") {
                Line("Collected", r.collected, tone = c.flowIn)
                Line("Expected", r.expected)
                Line("Outstanding", r.outstanding, tone = c.flowOut)
                PlainLine("Units paid", "${r.paidCount}/${r.total}")
            }

            Section("Expenses this month") {
                if (r.expenses.isEmpty()) Text("No expenses recorded.", color = c.textTertiary, style = MaterialTheme.typography.bodySmall)
                r.expenses.forEach { (cat, amt) -> Line(cat.label, amt, tone = c.flowOut) }
                Line("Total spent", r.totalSpent, tone = c.flowOut, strong = true)
            }

            Section("Paid this month · ${r.paidList.size}") {
                if (r.paidList.isEmpty()) Text("No payments yet.", color = c.textTertiary, style = MaterialTheme.typography.bodySmall)
                r.paidList.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.name} · ${p.owner}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            if (p.partial) Text("partial", color = c.warn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        DualMoney(p.amount, compact = true, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Section("Box balance") {
                Line("Closing (last month)", r.opening)
                Line("Total collected", r.collected, tone = c.flowIn, sign = "+")
                Line("Total spent", r.totalSpent, tone = c.flowOut, sign = "−")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("This month ${if (r.isGain) "▲ gain" else "▼ loss"}", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    DualMoney(r.net, compact = true, style = MaterialTheme.typography.bodySmall, sign = if (r.isGain) "+" else "−")
                }
                Line("Closing · available", r.closing, strong = true)
            }

            if (r.unpaid.isNotEmpty()) {
                Section("Still due") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.unpaid.forEach { (name, owner) ->
                            Box(Modifier.clip(RoundedCornerShape(50)).background(c.flowOutSoft).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("$name · $owner", color = c.flowOut, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title.uppercase(), color = c.textTertiary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
}

@Composable
private fun Line(key: String, value: DualAmount, tone: Color? = null, strong: Boolean = false, sign: String = "") {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = if (strong) MaterialTheme.colorScheme.onSurface else c.textSecondary, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
        DualMoney(value, compact = true, sign = sign, style = MaterialTheme.typography.bodySmall, weight = if (strong) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun PlainLine(key: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}
