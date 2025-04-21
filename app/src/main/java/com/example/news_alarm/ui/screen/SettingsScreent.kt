// --- ui/screen/SettingsScreen.kt ---
package com.example.news_alarm.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.news_alarm.worker.scheduleNewsChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("news_prefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val options = listOf("15분", "30분", "1시간", "2시간")
    val optionToMinutes = mapOf(
        "15분" to 15L,
        "30분" to 30L,
        "1시간" to 60L,
        "2시간" to 120L
    )

    var selectedOption by remember {
        mutableStateOf(
            options.find {
                optionToMinutes[it] == prefs.getLong("check_interval_minutes", 15L)
            } ?: "15분"
        )
    }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("🔔 알림 주기 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Button(onClick = onBack) {
                    Text("⬅ 돌아가기")
                }
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedOption,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("알림 주기") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedOption = option
                                expanded = false

                                val interval = optionToMinutes[option] ?: 15L
                                prefs.edit().putLong("check_interval_minutes", interval).apply()
                                scheduleNewsChecker(context, interval)
                                // Show snackbar message
                                scope.launch {
                                    snackbarHostState.showSnackbar("✅ 알림 주기가 $option 으로 설정되었어요.")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}