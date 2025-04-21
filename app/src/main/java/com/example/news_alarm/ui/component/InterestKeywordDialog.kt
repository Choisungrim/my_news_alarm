package com.example.news_alarm.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun InterestKeywordDialog(
    categories: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val tempSelection = remember { mutableStateListOf<String>().apply { addAll(selected) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("🔔 관심 카테고리 선택", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                categories.forEach { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = category in tempSelection,
                                onValueChange = {
                                    if (category in tempSelection) tempSelection.remove(category)
                                    else tempSelection.add(category)
                                }
                            )
                            .padding(8.dp)
                    ) {
                        Checkbox(checked = category in tempSelection, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(category)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onSave(tempSelection.toSet()) }) { Text("저장") }
                }
            }
        }
    }
}