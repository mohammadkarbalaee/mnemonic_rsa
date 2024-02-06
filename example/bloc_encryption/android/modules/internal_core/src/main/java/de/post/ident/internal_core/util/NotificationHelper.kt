package de.post.ident.internal_core.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(
        context: Context,
        id: String,
        name: String,
        description: String?,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val channel = NotificationChannel(id, name, importance)
        if (description != null) {
            channel.description = description
        }

        // workaround to _disable_ vibration on Android 8 ('duh)
        // https://stackoverflow.com/questions/46402510/notification-vibrate-issue-for-android-8-0/47646166#47646166
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0)
        //

        with(NotificationManagerCompat.from(context)) {
            createNotificationChannel(channel)
        }
    }

    fun createNotification(
        context: Context,
        channelId: String,
        titleText: String,
        messageText: String?,
        smallIconResId: Int,
        largeIcon: Bitmap? = null,
        contentIntent: PendingIntent,
        autoCancel: Boolean = true,
        onGoing: Boolean = false,
        actionText: String? = null,
        actionIconResId: Int? = null,
        actionPendingIntent: PendingIntent? = null
    ) : Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setOngoing(onGoing)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(autoCancel)
            .setContentTitle(titleText)
            .setTicker(titleText)
            .setSmallIcon(smallIconResId)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)

        if (messageText != null) {
            builder.setContentText(messageText)
        }

        if (actionText != null && actionIconResId != null && actionPendingIntent != null) {
            builder.addAction(actionIconResId, actionText, actionPendingIntent)
        }

        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun showNotification(
        context: Context,
        notification: Notification,
        notificationId: Int
    ) = with(NotificationManagerCompat.from(context)) {
            notify(notificationId, notification)
        }

    fun cancelNotification(
        context: Context,
        notificationId: Int
    ) = with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
}