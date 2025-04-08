package com.example.news_alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable

// 데이터 모델
data class NewsItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val summary: String,
    val category: String
)

// 뷰모델 상태
data class UiState(
    val items: List<NewsItem> = emptyList(),
    val selectedItem: NewsItem? = null,
    val selectedCategory: String = "전체",
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class NewsViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setCategory(category: String) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun select(item: NewsItem) {
        _state.update { it.copy(selectedItem = item) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedItem = null) }
    }

    fun load(context: Context) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val feeds = fetchFeedUrlsFromAssets(context)
        val allItems = feeds.flatMap { fetchRssFeed(it).take(3) }
            .filter { isToday(it.pubDate) } // ✅ 오늘자 뉴스만
            .sortedByDescending { parseDate(it.pubDate) }
        _state.update {
            it.copy(
                items = allItems,
                isLoading = false,
                selectedCategory = "전체",
                searchQuery = ""
            )
        }
    }

    fun getFilteredItems(): List<NewsItem> {
        val base = if (state.value.selectedCategory == "전체") state.value.items else
            state.value.items.filter { it.category == state.value.selectedCategory }
        return base.filter {
            it.title.contains(state.value.searchQuery, ignoreCase = true) ||
                    it.description.contains(state.value.searchQuery, ignoreCase = true)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleNewsChecker(applicationContext)
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

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
                Text("⭐ 관심 키워드 설정")
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

@Composable
fun DetailScreen(viewModel: NewsViewModel, onBack: () -> Unit) {
    val item = viewModel.state.collectAsState().value.selectedItem
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (item == null) {
        Text("❗ 뉴스 상세 정보가 없습니다", modifier = Modifier.padding(16.dp))
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(item.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) {
                Text("⬅ 돌아가기")
            }
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                context.startActivity(intent)
            }) {
                Text("🌐 원문 보기")
            }
        }
    }
}


@Composable
fun CategoryDropdown(categories: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text("카테고리: $selected")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: NewsViewModel = viewModel()

    NavHost(navController, startDestination = "rss") {
        composable("rss") {
            RssScreen(navController, viewModel)
        }
        composable("detail") {
            DetailScreen(viewModel = viewModel) {
                viewModel.clearSelection()
                navController.popBackStack()
            }
        }
    }
}

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

suspend fun fetchFeedUrlsFromAssets(context: Context): List<String> = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.assets.open("feed_specs.csv")
        val lines = inputStream.bufferedReader().readLines()
        val header = lines.firstOrNull()?.split(",") ?: return@withContext emptyList()
        val urlIndex = header.indexOf("url")
        if (urlIndex == -1) return@withContext emptyList()
        lines.drop(1).mapNotNull {
            val cols = it.split(",")
            cols.getOrNull(urlIndex)?.trim()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun fetchRssFeed(url: String): List<NewsItem> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    try {
        client.newCall(request).execute().use { response ->
            val xml = response.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val items = doc.select("item").take(3)
            items.mapNotNull { element ->
                try {
                    val title = parseHtmlText(element.selectFirst("title")?.text() ?: return@mapNotNull null)
                    val link = element.selectFirst("link")?.text() ?: return@mapNotNull null
                    val descriptionRaw = element.selectFirst("description")?.text() ?: ""
                    val description = parseHtmlText(descriptionRaw)
                    val pubDate = element.selectFirst("pubDate")?.text() ?: ""
                    val summaryRow = element.text() ?: ""
                    val summary = parseHtmlText(summaryRow)
                    val category = element.selectFirst("category")?.text() ?: ""
                    NewsItem(title, link, description, pubDate, summary, category)
                } catch (e: Exception) {
                    null
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun parseDate(pubDate: String): Long {
    return try {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        format.parse(pubDate)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun parseHtmlText(html: String): String {
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
}

fun isToday(pubDate: String): Boolean {
    return try {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        val date = format.parse(pubDate)
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal2.time = date ?: return false
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
        false
    }
}

fun getCurrentTimeFormatted(): String {
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    return format.format(Date())
}
