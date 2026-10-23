package com.lillans.image.blixtsvar.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AutostartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val start = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(start)
            } else {
                context.startService(start)
            }
        }
    }
}
