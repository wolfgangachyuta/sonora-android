package com.sonora.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SonoraFetchService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        when (command.action) {
            ACTION_CANCEL -> {
                SonoraFetch.cancel()
                downloadJob?.cancel()
                SonoraFetch.updateStatus(FetchStatus(message = "Download cancelled."))
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager().cancel(NOTIFICATION_ID)
                stopSelf(startId)
            }
            ACTION_DOWNLOAD -> {
                val url = command.getStringExtra(EXTRA_URL).orEmpty()
                if (url.isBlank()) {
                    SonoraFetch.updateStatus(FetchStatus(error = "No source URL was supplied."))
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                createNotificationChannel()
                startInForeground(buildNotification("Preparing download…", 0, true))
                downloadJob?.cancel()
                downloadJob = serviceScope.launch {
                    SonoraFetch.updateStatus(FetchStatus(active = true, message = "Initialising downloader…"))
                    try {
                        SonoraFetch.initialize(applicationContext)
                        val file = SonoraFetch.downloadAudio(applicationContext, url) { percent, eta ->
                            val current = SonoraFetch.status.value
                            updateNotification(buildNotification("Downloading audio · $percent%", percent, false))
                            SonoraFetch.updateStatus(current.copy(active = true, progress = percent, etaSeconds = eta, message = "Downloading audio…"))
                        }
                        SonoraFetch.updateStatus(FetchStatus(message = "Download complete.", progress = 100, savedPath = file.absolutePath))
                        notificationManager().notify(NOTIFICATION_ID, buildNotification("Download complete", 100, false, cancellable = false))
                    } catch (cancelled: CancellationException) {
                        SonoraFetch.updateStatus(FetchStatus(message = "Download cancelled."))
                        throw cancelled
                    } catch (failure: Throwable) {
                        SonoraFetch.updateStatus(FetchStatus(error = failure.message ?: "Download failed."))
                        notificationManager().notify(NOTIFICATION_ID, buildNotification("Download failed", 0, false, cancellable = false))
                    } finally {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf(startId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(notification: Notification) {
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SONORA Fetch", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean, cancellable: Boolean = true): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 2, Intent(this, SonoraFetchService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SONORA Fetch")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(indeterminate || progress in 1..99)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .apply { if (cancellable) addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel) }
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.sonora.player.action.DOWNLOAD"
        const val ACTION_CANCEL = "com.sonora.player.action.CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        private const val CHANNEL_ID = "sonora_fetch"
        private const val NOTIFICATION_ID = 4201

        fun start(context: android.content.Context, url: String) {
            val intent = Intent(context, SonoraFetchService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: android.content.Context) {
            context.startService(Intent(context, SonoraFetchService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
