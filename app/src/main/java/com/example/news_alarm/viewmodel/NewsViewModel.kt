package com.example.news_alarm.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.news_alarm.model.NewsItem
import com.example.news_alarm.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun setCategory(category: String) = _state.update { it.copy(selectedCategory = category) }
    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }
    fun select(item: NewsItem) = _state.update { it.copy(selectedItem = item) }
    fun clearSelection() = _state.update { it.copy(selectedItem = null) }

    fun load(context: Context) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val feeds = fetchFeedUrlsFromAssets(context)
        val items = feeds.flatMap { fetchRssFeed(it).take(3) }
            .filter { isToday(it.pubDate) }
            .sortedByDescending { parseDate(it.pubDate) }

        _state.update {
            it.copy(
                items = items,
                isLoading = false,
                selectedCategory = "전체",
                searchQuery = ""
            )
        }
    }

    fun getFilteredItems(): List<NewsItem> {
        val base = if (state.value.selectedCategory == "전체") state.value.items
        else state.value.items.filter { it.category == state.value.selectedCategory }

        return base.filter {
            it.title.contains(state.value.searchQuery, true) ||
                    it.description.contains(state.value.searchQuery, true)
        }
    }
}