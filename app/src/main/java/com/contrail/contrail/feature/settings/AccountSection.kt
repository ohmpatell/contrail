package com.contrail.contrail.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.contrail.contrail.core.data.sync.AccountState
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.time.Duration
import java.time.Instant

/**
 * Optional account: sign in to keep your phone and tablet in step. A username and password,
 * nothing else; no email is asked for or sent.
 */
@Composable
fun AccountSection(
    account: AccountState,
    busy: Boolean,
    error: String?,
    nowMs: Long,
    onEnter: (username: String, password: String, create: Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    if (!account.available) {
        Text("Sync isn't set up in this build.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        return
    }
    AnimatedContent(account.signedIn, label = "account") { signedIn ->
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LivePulse(color = if (account.error == null) colors.statusOnTime else colors.statusDelayed)
                    Column(Modifier.weight(1f)) {
                        Text("@${account.username}", style = ContrailTheme.type.title, color = colors.textPrimary)
                        Text(
                            when {
                                account.syncing -> "Syncing…"
                                account.error != null -> account.error
                                account.lastSyncMs != null -> "Synced ${ago(account.lastSyncMs, nowMs)}"
                                else -> "Waiting to sync"
                            },
                            style = ContrailTheme.type.bodySmall,
                            color = if (account.error != null) colors.statusDelayed else colors.textTertiary,
                        )
                    }
                }
                Text(
                    "Trips, flights, lounge visits, your watchlist and favourite airports stay in step on every device you sign in on.",
                    style = ContrailTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    AccentButton("Sync now", onSyncNow, enabled = !account.syncing)
                    AccentButton("Sign out", onSignOut, secondary = true)
                }
            } else {
                SignInForm(busy, error, onEnter)
            }
        }
    }
}

@Composable
private fun SignInForm(busy: Boolean, error: String?, onEnter: (String, String, Boolean) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Text(
        "Optional. Sign in to keep your phone and tablet in step, and later to share flights. Just a username and password: no email.",
        style = ContrailTheme.type.bodySmall,
        color = colors.textSecondary,
    )
    Field("Username", username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24) }, secret = false)
    Field("Password", password, { password = it.take(72) }, secret = true)
    error?.let { Text(it, style = ContrailTheme.type.bodySmall, color = colors.statusCancelled) }
    val ready = username.length >= 3 && password.length >= 8 && !busy
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        AccentButton(if (busy) "Signing in…" else "Sign in", { onEnter(username, password, false) }, enabled = ready)
        AccentButton("Create account", { onEnter(username, password, true) }, secondary = true, enabled = ready)
    }
    Text("Passwords are at least 8 characters. There's no reset by email, so keep it somewhere safe.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, secret: Boolean) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(label.uppercase(), style = ContrailTheme.type.eyebrow, color = colors.textTertiary)
        Box(
            Modifier
                .fillMaxWidth()
                .border(ContrailTheme.sizes.hairline, colors.glassBorderTop, ContrailTheme.shapes.small)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
                    imeAction = if (secret) ImeAction.Done else ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ago(thenMs: Long, nowMs: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(thenMs), Instant.ofEpochMilli(nowMs))
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} days ago"
    }
}
