package com.example.news_alarm.util

import android.content.Context
import android.text.Html
import com.example.news_alarm.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.*

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
                    val summary = parseHtmlText(element.text() ?: "")
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
        val cal2 = Calendar.getInstance().apply { time = date ?: return false }
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