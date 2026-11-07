package com.contrail.contrail.feature.trips

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contrail.contrail.core.data.sync.SentShare
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.theme.ContrailTheme

/** Send this trip to another Contrail user by username. */
@Composable
fun ShareTripDialog(
    signedIn: Boolean,
    sending: Boolean,
    message: String?,
    error: String?,
    onSend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var username by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), level = GlassLevel.Strong, hazeState = null) {
            Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Eyebrow("Share this trip", color = colors.accent)
                Text(
                    "It appears in their Contrail under Following, with a live update on the day and an alert when you land. Your edits reach them too.",
                    style = ContrailTheme.type.bodySmall, color = colors.textSecondary,
                )
                if (!signedIn) {
                    Text("Sign in first, in Settings → Account and sync.", style = ContrailTheme.type.body, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        AccentButton("Open Settings", onOpenSettings)
                        AccentButton("Not now", onDismiss, secondary = true)
                    }
                    return@Column
                }
                Text("THEIR USERNAME", style = ContrailTheme.type.eyebrow, color = colors.textTertiary)
                Box(
                    Modifier.fillMaxWidth().border(ContrailTheme.sizes.hairline, colors.glassBorderTop, ContrailTheme.shapes.small).padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@", style = ContrailTheme.type.body, color = colors.textTertiary)
                        BasicTextField(
                            value = username,
                            onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24) },
                            singleLine = true,
                            textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Send),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.statusCancelled) }
                message?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.statusOnTime) }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    AccentButton(if (sending) "Sending…" else "Share", { onSend(username) }, enabled = username.length >= 3 && !sending)
                    AccentButton(if (message != null) "Done" else "Cancel", onDismiss, secondary = true)
                }
            }
        }
    }
}

/** On a trip someone shared with you: who, and what you can do with it. */
@Composable
fun FollowingBanner(sharedBy: String?, onJoin: () -> Unit, onUnfollow: () -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Following") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            LivePulse(color = colors.accent)
            Text(
                "Shared by @${sharedBy ?: "someone"}. You'll get a live update on the day and an alert when it lands.",
                style = ContrailTheme.type.bodySmall, color = colors.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AccentButton("I'm on this trip too", onJoin)
            AccentButton("Stop following", onUnfollow, secondary = true)
        }
        Text(
            "Joining adds these flights to your own log and Passport, marked as flown together.",
            style = ContrailTheme.type.bodySmall, color = colors.textTertiary,
        )
    }
}

/** On your own trip: who can see it. */
@Composable
fun SharedWithCard(shares: List<SentShare>, onUnshare: (SentShare) -> Unit) {
    val colors = ContrailTheme.colors
    SectionCard("Shared with") {
        shares.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${s.username}", style = ContrailTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Stop sharing", style = ContrailTheme.type.label, color = colors.accent,
                    modifier = Modifier.clickable(role = Role.Button) { onUnshare(s) },
                )
            }
        }
    }
}
