package com.buildingbox.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd

/** "$15 + 1.5M LL" in tabular mono — two pots, never converted. Zero parts omitted. */
@Composable
fun DualMoney(
    amount: DualAmount,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    sign: String = "",
    style: TextStyle = LocalTextStyle.current,
    weight: FontWeight = FontWeight.Bold,
    stacked: Boolean = false,
) {
    val c = LocalAppColors.current
    val mono = LocalAppFonts.current.mono

    if (stacked) {
        Column(modifier) {
            if (!amount.hasUsd && !amount.hasLbp) {
                Text("$0", style = style, fontFamily = mono, fontWeight = weight)
            }
            if (amount.hasUsd) {
                Text("$sign${formatUsd(amount.usdCents, compact)}", style = style, fontFamily = mono, fontWeight = weight)
            }
            if (amount.hasLbp) {
                Text("$sign${formatLbp(amount.lbp, compact)}", style = style, fontFamily = mono, fontWeight = weight, color = if (amount.hasUsd) c.textSecondary else style.color)
            }
        }
        return
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        if (!amount.hasUsd && !amount.hasLbp) {
            Text("$0", style = style, fontFamily = mono, fontWeight = weight)
            return@Row
        }
        if (amount.hasUsd) {
            Text("$sign${formatUsd(amount.usdCents, compact)}", style = style, fontFamily = mono, fontWeight = weight)
        }
        if (amount.hasUsd && amount.hasLbp) {
            Text("+", style = style, color = c.textTertiary, fontWeight = FontWeight.Medium)
        }
        if (amount.hasLbp) {
            Text("$sign${formatLbp(amount.lbp, compact)}", style = style, fontFamily = mono, fontWeight = weight, color = if (amount.hasUsd) c.textSecondary else style.color)
        }
    }
}

/** Plain-string dual label for inline use, e.g. "$15 + 1.5M LL". */
fun dualString(amount: DualAmount, compact: Boolean = true): String = buildList {
    if (amount.hasUsd) add(formatUsd(amount.usdCents, compact))
    if (amount.hasLbp) add(formatLbp(amount.lbp, compact))
    if (isEmpty()) add("$0")
}.joinToString(" + ")
