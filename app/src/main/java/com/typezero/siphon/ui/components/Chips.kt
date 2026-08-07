package com.typezero.siphon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.typezero.siphon.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    if (singleLine) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dpSafe())
        ) {
            items(options) { option ->
                Chip(option, selected, label, onSelect)
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dpSafe())
        ) {
            options.forEach { option ->
                Chip(option, selected, label, onSelect)
            }
        }
    }
}

@Composable
private fun <T> Chip(
    option: T,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val isSelected = option == selected
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(option) },
        label = { Text(label(option)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = SiphonSurfaceBright,
            labelColor = SiphonTextMuted,
            selectedContainerColor = SiphonPurple,
            selectedLabelColor = Color.White
        ),
        border = BorderStroke(
            width = 1.dpSafe(),
            color = if (isSelected) SiphonPurpleBright else SiphonOutline
        )
    )
}
