package com.buildingbox.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    inset: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (inset) c.surfaceInset else c.surface,
        border = if (inset) null else BorderStroke(1.dp, c.hairline),
        shadowElevation = if (inset) 0.dp else 1.dp,
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun TopBar(title: String, eyebrow: String? = null, actions: @Composable (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Row(
        Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow.uppercase(), color = c.textTertiary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        actions?.invoke()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val hue = (name.sumOf { it.code } % 360).toFloat()
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    Box(
        Modifier.size(size).clip(RoundedCornerShape(35))
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.72f), Color.hsv((hue + 40f) % 360f, 0.5f, 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp, fontFamily = LocalAppFonts.current.display)
    }
}

enum class PillTone { POSITIVE, WARNING, NEGATIVE, NEUTRAL }

@Composable
fun StatusPill(text: String, tone: PillTone) {
    val c = LocalAppColors.current
    val (fg, bg) = when (tone) {
        PillTone.POSITIVE -> c.flowIn to c.flowInSoft
        PillTone.WARNING -> c.warn to c.warnSoft
        PillTone.NEGATIVE -> c.flowOut to c.flowOutSoft
        PillTone.NEUTRAL -> c.textTertiary to c.surfaceInset
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(text.uppercase(), color = fg, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.4.sp)
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    ghost: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = if (ghost) {
            ButtonDefaults.buttonColors(containerColor = c.surfaceInset, contentColor = MaterialTheme.colorScheme.onSurface)
        } else {
            ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.onPrimary)
        },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surfaceInset,
            disabledContainerColor = c.surfaceInset,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}
