package com.superchant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superchant.R
import com.superchant.data.RoundSize

/**
 * Chips for beads per round: the common mala sizes plus Custom. With [defaultSize] set, a leading
 * "Default" chip stands for null (follow the app setting), as used by goals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundSizePicker(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    defaultSize: Int? = null
) {
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = selected != null && selected !in RoundSize.PRESETS

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (defaultSize != null) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.round_size_default, defaultSize)) }
            )
        }
        RoundSize.PRESETS.forEach { size ->
            FilterChip(
                selected = selected == size,
                onClick = { onSelect(size) },
                label = { Text(size.toString()) }
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustom = true },
            label = {
                Text(if (isCustom) stringResource(R.string.round_size_custom_value, selected) else stringResource(R.string.round_size_custom))
            }
        )
    }

    if (showCustom) {
        CustomRoundSizeDialog(
            initial = selected ?: defaultSize ?: RoundSize.DEFAULT,
            onConfirm = {
                onSelect(it)
                showCustom = false
            },
            onDismiss = { showCustom = false }
        )
    }
}

@Composable
private fun CustomRoundSizeDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && RoundSize.isValid(value)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.round_size_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) text = it },
                label = { Text(stringResource(R.string.round_size_title)) },
                singleLine = true,
                isError = !valid,
                supportingText = { Text(stringResource(R.string.round_size_range, RoundSize.MIN, RoundSize.MAX)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.takeIf(RoundSize::isValid)?.let(onConfirm) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.takeIf(RoundSize::isValid)?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
