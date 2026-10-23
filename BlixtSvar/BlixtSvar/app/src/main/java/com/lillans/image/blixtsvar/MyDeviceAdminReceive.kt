package com.lillans.image.blixtsvar

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    // Triggeras när Device Admin är aktiverad
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin aktiverad", Toast.LENGTH_SHORT).show()
    }

    // Triggeras när Device Admin är inaktiverad
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Device Admin inaktiverad", Toast.LENGTH_SHORT).show()
    }

    // Du kan lägga till fler metoder beroende på dina behov
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        // Hantera lösenordsändringar om det behövs
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // Hantera misslyckade lösenord om det behövs
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        // Hantera lyckade lösenord om det behövs
    }

    // Andra metoder beroende på vad din app kräver
}