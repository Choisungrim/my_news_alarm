package com.example.news_alarm.worker

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.news_alarm.model.NewsItem
import com.example.news_alarm.util.fetchFeedUrlsFromAssets
import com.example.news_alarm.util.fetchRssFeed
import com.example.news_alarm.util.isToday
import java.util.concurrent.TimeUnit

class NewsCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("news_prefs", Context.MODE_PRIVATE)
        val keywords = prefs.getStringSet("interests", emptySet()) ?: emptySet()
        val notifiedLinks = prefs.getStringSet("notified_links", emptySet())?.toMutableSet() ?: mutableSetOf()

        val feeds = fetchFeedUrlsFromAssets(context)
        val todayItems = feeds.flatMap { fetchRssFeed(it).take(3) }.filter { isToday(it.pubDate) }

        val matched = todayItems.filter { item ->
            (item.link !in notifiedLinks) && keywords.any { keyword ->
                item.title.contains(keyword, ignoreCase = true) ||
                        item.description.contains(keyword, ignoreCase = true)
            }
        }

        if (matched.isNotEmpty()) {
            createNotificationChannel(context)
            notifyUser(context, matched.first())

            // 새 링크 저장 (50개로 제한)
            notifiedLinks.add(matched.first().link)
            while (notifiedLinks.size > 50) {
                notifiedLinks.remove(notifiedLinks.first())
            }
            prefs.edit().putStringSet("notified_links", notifiedLinks).apply()
        }
        return Result.success()
    }

    private fun notifyUser(context: Context, item: NewsItem) {
        createNotificationChannel(context)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, pendingIntentFlags
        )

        val builder = NotificationCompat.Builder(context, "rss_alerts")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("🔔 관심 뉴스 도착!")
            .setContentText(item.title.take(100)) // 길이 제한 보정
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.summary.take(300)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 진동, 사운드 포함

        val notificationManager = NotificationManagerCompat.from(context)

        if (notificationManager.areNotificationsEnabled()) {
            try {
                // 알림 ID를 링크의 해시로 생성하여 고유하게
                val notificationId = item.link.hashCode()
                notificationManager.notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                Log.e("NotifyUser", "알림 권한 문제: ${e.message}")
            }
        } else {
            Log.w("NotifyUser", "알림 비활성화 상태입니다.")
        }
    }


    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "RSS 알림"
            val desc = "관심 키워드 기반 뉴스 알림"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("rss_alerts", name, importance).apply {
                description = desc
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

fun scheduleNewsChecker(context: Context, intervalMinutes: Long) {
    // 최소 주기 제한 (WorkManager는 15분 이상만 허용)
    val safeInterval = intervalMinutes.coerceAtLeast(15)

    val request = PeriodicWorkRequestBuilder<NewsCheckWorker>(safeInterval, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "rss_check_work",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
