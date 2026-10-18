package com.superchant.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superchant.R
import com.superchant.ui.theme.pressScale

/** Start on idle Home: a large pill. */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SessionButton(
        label = stringResource(R.string.action_start),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
    )
}

/** Stop for a running session: one tap ends and saves it. */
@Composable
fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SessionButton(
        label = stringResource(R.string.action_stop),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        textStyle = MaterialTheme.typography.titleMedium,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SessionButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Text(text = label, style = textStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}
