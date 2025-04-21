package com.example.news_alarm.ui.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.news_alarm.ui.component.CategoryDropdown
import com.example.news_alarm.ui.component.InterestKeywordDialog
import com.example.news_alarm.util.getCurrentTimeFormatted
import com.example.news_alarm.viewmodel.NewsViewModel

@Composable
fun RssScreen(navController: NavHostController, viewModel: NewsViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var lastLoadedTime = getCurrentTimeFormatted();
    var initialLoad by remember { mutableStateOf(true) }
    val hasLoaded = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasLoaded.value) {
            viewModel.load(context)
            hasLoaded.value = true
        }
    }

    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🕒 마지막 로드: $lastLoadedTime", style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { viewModel.load(context); lastLoadedTime = getCurrentTimeFormatted() } }) {
                if (state.isLoading) Text("⏳ 로딩 중...") else Text("🔄 새로고침")
            }
            Button(onClick = { showDialog = true }) {
                Text("⭐ 관심 키워드")
            }
            Button(onClick = { navController.navigate("settings") }) {
                Text("⚙️ 설정")
            }

        }


        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("🔍 검색") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        CategoryDropdown(
            categories = listOf("전체") + state.items.map { it.category }.filter { it.isNotBlank() }.distinct(),
            selected = state.selectedCategory,
            onSelected = { viewModel.setCategory(it) }
        )

        Spacer(Modifier.height(12.dp))

        val filtered = viewModel.getFilteredItems()

        if (!state.isLoading && filtered.isEmpty()) {
            Text("📭 표시할 뉴스가 없습니다", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn {
                items(filtered) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                viewModel.select(item)
                                navController.navigate("detail")
                            },
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(item.summary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(item.pubDate, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (showDialog) {
            val uniqueCategories = state.items.map { it.category }.filter { it.isNotBlank() }.distinct()
            val prefs = context.getSharedPreferences("news_prefs", Context.MODE_PRIVATE)
            val selected = prefs.getStringSet("interests", emptySet()) ?: emptySet()

            InterestKeywordDialog(
                categories = uniqueCategories,
                selected = selected,
                onDismiss = { showDialog = false },
                onSave = { set ->
                    prefs.edit().putStringSet("interests", set).apply()
                    showDialog = false
                }
            )
        }
    }
}