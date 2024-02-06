package de.post.ident.internal_video

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.NotificationHelper
import de.post.ident.internal_core.util.UiUtil
import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.ui.VideoIdentActivity
import kotlinx.coroutines.*
import java.util.*
import kotlin.reflect.KClass

class VideoChatService : Service() {
    private val stopServiceRequestCode = 0x77882233
    private val serviceId = 0xcafebabe.toInt()
    private val serviceNotificationChannelId = "notificationVideochatService"
    private val warningNotificationChannelId = "notificationVideochatInBackground"
    private val backgroundWarningNotificationId = 7862368
    private val binder = VideoChatServiceBinder()
    private var backgroundTimerJob: Job? = null

    private val DELAY_BACKGROUND_WARNING: Long = 29 * 60 * 1000
    private val DELAY_BACKGROUND_CANCELLATION: Long = 60 * 1000

    companion object {
        private const val actionStopCall = "UI.STOP"

        fun start(ctx: Context) {
            val serviceIntent = Intent(ctx, VideoChatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VideoChatService::class.java))
        }

        fun isHangupIntent(intent: Intent) = intent.getBooleanExtra(actionStopCall, false)
    }

    private lateinit var notificationManager: NotificationManager
    private val videoManager: VideoManager?
        get() = try {
                VideoManager.instance()
            } catch (e: Exception) {
                log("Video manager not available")
                null
            }

    override fun onCreate() {
        super.onCreate()
        log("Service started")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }

        val serviceNotification: Notification = createServiceNotification(applicationContext, VideoIdentActivity::class)
        notificationManager.notify(serviceId, serviceNotification)
        videoManager?.setSpeakerActive(true)
        startForeground(serviceId, serviceNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("Service destroyed")
        notificationManager.cancel(serviceId)
        videoManager?.setSpeakerActive(false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannels() {
        // service notification
        var pushChannelName = LocalizedStrings.getString("push_channel_service_name")
        if (pushChannelName.isEmpty()) {
            pushChannelName = "Video chat"
        }
        NotificationHelper.createNotificationChannel(
            this,
            serviceNotificationChannelId,
            pushChannelName,
            LocalizedStrings.getString("push_channel_service_description")
        )

        // background warning notifications
        var backgroundWarningNotificationChannelName = LocalizedStrings.getString("important_notification_channel_title")
        if (backgroundWarningNotificationChannelName.isEmpty()) {
            backgroundWarningNotificationChannelName = "Important notifications"
        }
        NotificationHelper.createNotificationChannel(
            this,
            warningNotificationChannelId,
            backgroundWarningNotificationChannelName,
            null,
            importance = NotificationManager.IMPORTANCE_MAX
        )
    }

    private fun createServiceNotification(context: Context, aClass: KClass<*>): Notification {
        log("Creating service notification")

        val contentTitle = LocalizedStrings.getString("service_notification_title")
        val resultPendingIntent: PendingIntent = createReturnIntent(context, aClass)
        val actionEndCallPendingIntent: PendingIntent = createEndCallIntent(context, aClass)

        return NotificationHelper.createNotification(
            this,
            serviceNotificationChannelId,
            contentTitle,
            null,
            R.drawable.pi_ic_posthorn,
            UiUtil.getBitmapFromVectorDrawable(this, R.drawable.pi_ic_posthorn),
            resultPendingIntent,
            false,
            true,
            LocalizedStrings.getString("end_call"),
            R.drawable.pi_ic_phone,
            actionEndCallPendingIntent
        )
    }

    private fun createReturnIntent(context: Context, activityClass: KClass<*>): PendingIntent {
        log("Creating return intent")
        val returnIntent = Intent(context, activityClass.java)
        returnIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            context,
            0,
            returnIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createEndCallIntent(context: Context, aClass: KClass<*>): PendingIntent {
        log("Creating end call intent")
        val stopIntent = Intent(context, aClass.java)
        stopIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        stopIntent.putExtra(actionStopCall, true)
        return PendingIntent.getActivity(
            context,
            stopServiceRequestCode,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun startBackgroundQueueHandling() = with(CoroutineScope(Dispatchers.Main)) {
        log("Start waiting queue handling for app in background")
        backgroundTimerJob = launch {
            delay(DELAY_BACKGROUND_WARNING)
            showBackgroundWarningNotification()
            backgroundTimerJob = launch {
                delay(DELAY_BACKGROUND_CANCELLATION)
                videoManager?.endCall()
                NotificationHelper.cancelNotification(this@VideoChatService, backgroundWarningNotificationId)
                showWaitingQueueLeftNotification()
            }
        }
    }

    fun cancelBackgroundQueueHandling() {
        log("Cancel waiting queue handling for app in background")
        NotificationHelper.cancelNotification(this, backgroundWarningNotificationId)
        backgroundTimerJob?.cancel(CancellationException(""))
    }

    private fun showBackgroundWarningNotification() {
        log("Creating background warning notification")
        val notification = NotificationHelper.createNotification(
            this,
            warningNotificationChannelId,
            LocalizedStrings.getString("in_background_come_back_title"),
            LocalizedStrings.getString("in_background_come_back_body"),
            R.drawable.pi_ic_posthorn,
            contentIntent = createReturnIntent(this, VideoIdentActivity::class)
        )
        NotificationHelper.showNotification(
            this,
            notification,
            backgroundWarningNotificationId
        )
    }

    private fun showWaitingQueueLeftNotification() {
        log("Creating waiting queue left notification")
        val notification = NotificationHelper.createNotification(
            this,
            warningNotificationChannelId,
            LocalizedStrings.getString("in_background_call_closed_title"),
            LocalizedStrings.getString("in_background_call_closed_body"),
            R.drawable.pi_ic_posthorn,
            contentIntent = createReturnIntent(this, VideoIdentActivity::class)
        )
        NotificationHelper.showNotification(
            this,
            notification,
            backgroundWarningNotificationId
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    inner class VideoChatServiceBinder : Binder() {
        val service: VideoChatService
            get() = this@VideoChatService
    }

    override fun onBind(intent: Intent?) = binder
}