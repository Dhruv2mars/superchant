package com.superchant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superchant.R
import com.superchant.service.VolumeKeyAccessibilityService

/**
 * Everything before the user reaches Android's accessibility settings. First a prominent disclosure
 * of exactly what the service does and doesn't do, which needs an explicit "I agree" (Google Play
 * requires this for apps that aren't accessibility tools). Once agreed, the setup steps, including the
 * restricted-settings help for sideloaded installs on Android 13+.
 * [onCountByTapping], when set, offers on-screen counting as a way out at every step.
 */
@Composable
fun AccessibilitySetupDialog(
    consented: Boolean,
    onConsent: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCountByTapping: (() -> Unit)? = null
) {
    if (!consented) {
        AccessibilityDisclosureDialog(onAgree = onConsent, onDecline = onDismiss, onCountByTapping = onCountByTapping)
        return
    }
    val context = LocalContext.current
    val needsRestrictedSettings = remember {
        VolumeKeyAccessibilityService.needsRestrictedSettingsPermission(context)
    }

    if (needsRestrictedSettings) {
        // Android 13+ sideloaded app - show two-step flow
        RestrictedSettingsDialog(
            onDismiss = onDismiss,
            onOpenAppInfo = {
                VolumeKeyAccessibilityService.openAppSettings(context)
            },
            onOpenAccessibility = onOpenSettings,
            onCountByTapping = onCountByTapping
        )
    } else {
        // Standard flow - just open accessibility settings
        SimpleAccessibilityDialog(
            onDismiss = onDismiss,
            onOpenSettings = onOpenSettings,
            onCountByTapping = onCountByTapping
        )
    }
}

/** Escape hatch for anyone who can't or won't grant accessibility. */
@Composable
private fun CountByTappingButton(onClick: (() -> Unit)?) {
    if (onClick == null) return
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.count_by_tapping), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SimpleAccessibilityDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCountByTapping: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.accessibility_setup_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.accessibility_setup_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.accessibility_setup_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                CountByTappingButton(onClick = onCountByTapping)
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RestrictedSettingsDialog(
    onDismiss: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onCountByTapping: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.restricted_settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.restricted_settings_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Primary action - Open Accessibility Settings
                SetupStepCard(
                    title = stringResource(R.string.restricted_settings_step2),
                    description = stringResource(R.string.restricted_settings_step2_desc),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    titleColor = Color.Unspecified
                ) {
                    Button(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.enable_accessibility),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary info - If toggle is blocked
                SetupStepCard(
                    title = stringResource(R.string.restricted_settings_step1),
                    description = stringResource(R.string.restricted_settings_step1_desc),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    OutlinedButton(
                        onClick = onOpenAppInfo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.open_app_info),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                CountByTappingButton(onClick = onCountByTapping)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SetupStepCard(
    title: String,
    description: String,
    containerColor: Color,
    titleColor: Color,
    action: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            action()
        }
    }
}


/**
 * Prominent disclosure for the accessibility service: what it does, what it never does, and how to
 * turn it off. Only "I agree" continues to the system settings.
 */
@Composable
private fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onCountByTapping: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = stringResource(R.string.disclosure_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.disclosure_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                DisclosureHeading(stringResource(R.string.disclosure_does_title))
                DisclosurePoint(Icons.Outlined.Check, stringResource(R.string.disclosure_does_1))
                DisclosurePoint(Icons.Outlined.Check, stringResource(R.string.disclosure_does_2))
                DisclosureHeading(stringResource(R.string.disclosure_never_title))
                DisclosurePoint(Icons.Outlined.Block, stringResource(R.string.disclosure_never_1))
                DisclosurePoint(Icons.Outlined.Block, stringResource(R.string.disclosure_never_2))
                DisclosurePoint(Icons.Outlined.Block, stringResource(R.string.disclosure_never_3))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.disclosure_turn_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                CountByTappingButton(onClick = onCountByTapping)
            }
        },
        confirmButton = {
            Button(onClick = onAgree) { Text(stringResource(R.string.disclosure_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.disclosure_decline)) }
        }
    )
}

@Composable
private fun DisclosureHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun DisclosurePoint(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
