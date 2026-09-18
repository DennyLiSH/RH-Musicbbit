package com.rabbithole.musicbbit.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Generic single-choice dropdown over Material3 [ExposedDropdownMenuBox].
 *
 * Renders a read-only outlined field showing [optionLabel] of [selectedOption]
 * (or [placeholder] when null) and a popup menu with one item per [options]
 * entry. Callers keep their own wrapper composables for domain-specific
 * label mapping — this component owns only the shared chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceDropdown(
    label: String,
    options: List<T>,
    selectedOption: T?,
    optionLabel: @Composable (T) -> String,
    onOptionSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedOption?.let { optionLabel(it) } ?: placeholder.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            isError = isError,
            supportingText = if (supportingText != null) {
                { Text(supportingText) }
            } else {
                null
            },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
