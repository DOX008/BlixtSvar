package com.lillans.image.blixtsvar.keepalive

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.lillans.image.blixtsvar.R


class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        // ingenting extra här – kan lägga logik vid behov
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Bygg/visa en lågmäld "kör i bakgrunden"-notis
        val channelId = "keepalive_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(
                    channelId,
                    "BlixtSvar - Bakgrundsdrift",
                    NotificationManager.IMPORTANCE_MIN
                )
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
        }

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.lillans.image.blixtsvar.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("BlixtSvar är aktivt")
                .setContentText("Auto-svar körs i bakgrunden")
                .setSmallIcon(R.drawable.blixtt_24)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("BlixtSvar är aktivt")
                .setContentText("Auto-svar körs i bakgrunden")
                .setSmallIcon(R.drawable.blixtt_24)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        }

        // Starta som foreground service
        startForeground(1001, notif)

        // START_STICKY gör att systemet försöker starta om servicen efter att den dödats
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
        val p = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 1000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, p)
                } else {
                    // Fallback utan exakt alarm
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, p)
                }
            } catch (_: SecurityException) {
                // Om appen saknar rätt att schemalägga exakta alarm
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, p)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, p)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, p)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Om vi ändå dödas: försök starta om snabbt
        val restart = Intent(applicationContext, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restart)
        } else {
            applicationContext.startService(restart)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

