package com.buildingbox.app.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.designsystem.LocalAppFonts
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state = viewModel.state
    val c = LocalAppColors.current
    val display = LocalAppFonts.current.display
    var showPassword by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Decorative accent glow, top-right (matches the design's blurred blob).
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 80.dp, y = (-120).dp).size(320.dp)
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.30f), Color.Transparent)), CircleShape),
        )

        // One scroll column: when the keyboard opens, the focused field scrolls into view and
        // everything above it pushes up — nothing overlaps. Width-capped + centered on desktop.
        Column(
            Modifier.fillMaxSize().widthIn(max = 460.dp).align(Alignment.TopCenter)
                .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(c.accent, c.accentStrong))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Apartment, null, tint = MaterialTheme.colorScheme.onPrimary) }
                    Column {
                        Text("BuildingBox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Building expenses · Beirut", color = c.textTertiary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(56.dp))

            Text("The building's money,", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, fontFamily = display, letterSpacing = (-1).sp)
            Text("clear to\neveryone.", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, fontFamily = display, letterSpacing = (-1).sp, color = c.accent)
            Text(
                "Track dues, expenses and the box balance in USD & LBP — one source of truth for the whole building.",
                color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp).widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(28.dp))

            state.error?.let {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.flowOutSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = c.flowOut, modifier = Modifier.size(18.dp))
                        Text(it, color = c.flowOut, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                LabeledField("Email", state.email, viewModel::onEmail, KeyboardType.Email, ImeAction.Next)
                Spacer(Modifier.height(14.dp))
                LabeledField(
                    "Password", state.password, viewModel::onPassword, KeyboardType.Password, ImeAction.Done,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password", tint = c.textTertiary)
                        }
                    },
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.size(8.dp))
                        Text("Signing in…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Sign in", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Secured with Firebase Auth", color = c.textTertiary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "Accounts are created by the admin in Firebase Console.",
                    color = c.textTertiary, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Column {
        Text(label, color = c.textSecondary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            trailingIcon = trailing,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surface,
                unfocusedContainerColor = c.surfaceInset,
                focusedBorderColor = c.accent,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
