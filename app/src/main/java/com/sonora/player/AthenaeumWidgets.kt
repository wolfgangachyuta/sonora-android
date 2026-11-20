package com.sonora.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WidgetCopy(val label: String, val title: String, val subtitle: String, val progress: Int = 0, val showProgress: Boolean = false)

abstract class AthenaeumBaseWidgetProvider : AppWidgetProvider() {
    protected abstract fun content(books: List<AthenaeumBook>): WidgetCopy

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val books = AthenaeumDatabase.get(context).dao().backupBooks()
                val copy = content(books)
                withContext(Dispatchers.Main) {
                    ids.forEach { id ->
                        val views = RemoteViews(context.packageName, R.layout.athenaeum_widget).apply {
                            setTextViewText(R.id.widget_label, copy.label)
                            setTextViewText(R.id.widget_title, copy.title)
                            setTextViewText(R.id.widget_subtitle, copy.subtitle)
                            setViewVisibility(R.id.widget_progress, if (copy.showProgress) android.view.View.VISIBLE else android.view.View.GONE)
                            setProgressBar(R.id.widget_progress, 100, copy.progress.coerceIn(0, 100), false)
                            val open = Intent(context, MainActivity::class.java).putExtra("open_athenaeum", true)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            val click = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            setOnClickPendingIntent(R.id.widget_root, click)
                        }
                        manager.updateAppWidget(id, views)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class AthenaeumContinueWidgetProvider : AthenaeumBaseWidgetProvider() {
    override fun content(books: List<AthenaeumBook>): WidgetCopy {
        val book = books.filter { it.status == "Currently Reading" }.maxByOrNull { it.lastOpenedAt }
        return if (book == null) WidgetCopy("ATHENAEUM · CONTINUE READING", "Nothing in progress", "Open your library to choose a book.")
        else WidgetCopy("ATHENAEUM · CONTINUE READING", book.title, book.author.ifBlank { "Unknown author" } + " · " + book.progress + "% read", book.progress, true)
    }
}

class AthenaeumOverviewWidgetProvider : AthenaeumBaseWidgetProvider() {
    override fun content(books: List<AthenaeumBook>): WidgetCopy {
        val finished = books.count { it.status == "Finished" }
        val active = books.count { it.status == "Currently Reading" }
        return WidgetCopy("ATHENAEUM · YOUR LIBRARY", "${books.size} books", "$finished finished · $active in progress")
    }
}

class AthenaeumRandomWidgetProvider : AthenaeumBaseWidgetProvider() {
    override fun content(books: List<AthenaeumBook>): WidgetCopy {
        val book = books.randomOrNull()
        return if (book == null) WidgetCopy("ATHENAEUM · RANDOM PICK", "Your shelves are waiting", "Add a book to begin.")
        else WidgetCopy("ATHENAEUM · RANDOM PICK", book.title, book.author.ifBlank { book.status })
    }
}

object AthenaeumWidgetUpdater {
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            AthenaeumContinueWidgetProvider::class.java,
            AthenaeumOverviewWidgetProvider::class.java,
            AthenaeumRandomWidgetProvider::class.java
        ).forEach { provider ->
            val component = ComponentName(context, provider)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val update = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(update)
            }
        }
    }
}
