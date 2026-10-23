package com.lillans.image.blixtsvar.sms

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.lillans.image.blixtsvar.OverlayActivity
import com.lillans.image.blixtsvar.core.TriggerEngine
import com.lillans.image.blixtsvar.data.RuleStore

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BlixtSvar"
        private const val ACTION_SMS_SENT = "com.lillans.image.blixtsvar.ACTION_SMS_SENT"
        private val timeRegex = Regex("(\\d{1,2}.\\d{2})") // punkt matchar *vilket som helst* kolon-tecken i diagnostik
    }

    private fun shouldThrottle(from: String, context: Context): Boolean {
        val prefs = context.getSharedPreferences("cooldown", Context.MODE_PRIVATE)
        val key = "last_$from"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        val within = (now - last) < 30_000L // 30 sek
        if (!within) prefs.edit().putLong(key, now).apply()
        return within
    }

    private fun hexDumpAround(str: String): String {
        // Logga kodpunkter för att se t.ex. 003A (:) vs FF1A (：) etc.
        val sb = StringBuilder()
        for (ch in str) {
            sb.append(ch)
            sb.append("(U+")
            sb.append(ch.code.toString(16).uppercase().padStart(4, '0'))
            sb.append(") ")
        }
        return sb.toString().trim()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() action=${intent.action}")

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
            Log.d(TAG, "Ignorerar: inte SMS_RECEIVED_ACTION")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            Log.d(TAG, "Inga PDU-meddelanden i intent")
            return
        }

        val from = messages.first().displayOriginatingAddress ?: run {
            Log.d(TAG, "Saknar avsändare (displayOriginatingAddress)")
            return
        }
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        if (body.isEmpty()) {
            Log.d(TAG, "Tomt SMS-body efter trim")
            return
        }

        Log.d(TAG, "FROM=$from")
        Log.d(TAG, "BODY(raw)=$body")

        // Diagnostik: lista alla “tider” vi hittar och deras kodpunkter
        timeRegex.findAll(body).forEach { m ->
            val seg = m.groupValues[1]
            Log.d(TAG, "TIME token in raw: $seg  →  ${hexDumpAround(seg)}")
        }

        // Skydda mot loop – svara inte på våra egna utskick
        if (body.contains("[BlixtSvar]", ignoreCase = true)) {
            Log.d(TAG, "Ignorerar SMS som verkar vara vårt eget (BlixtSvar)")
            return
        }

        // Kör matchning (TriggerEngine gör all robust normalisering)
        val rules = RuleStore(context).load()
        Log.d(TAG, "Rules loaded: count=${rules.size}")
        rules.forEachIndexed { idx, r ->
            Log.d(TAG, "Rule[$idx]: id=${r.id} enabled=${r.enabled} pattern=/${r.pattern.pattern}/ reply=\"${r.reply}\"")
        }

        val engine = TriggerEngine(rules)
        val reply = engine.match(body) // OBS: vi skickar in *raw* här; TriggerEngine normaliserar
        if (reply == null) {
            Log.d(TAG, "Ingen match – autosvar skickas inte")
            return
        }
        Log.d(TAG, "MATCH! reply=\"$reply\"")

        if (shouldThrottle(from, context)) {
            Log.d(TAG, "Throttle aktivt för $from – avbryter")
            return
        }

        val hasSend = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasSend) {
            Log.d(TAG, "SEND_SMS saknas – skickar inget och visar ingen dialog")
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java)
        else
            SmsManager.getDefault()

        val sentIntent = Intent(ACTION_SMS_SENT).apply {
            setPackage(context.packageName)
            putExtra("to", from)
            putExtra("reply", reply)
        }
        val sentPendingIntent = PendingIntent.getBroadcast(
            context,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            sentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                when (resultCode) {
                    android.app.Activity.RESULT_OK -> {
                        Log.d(TAG, "SMS SENT OK till ${i.getStringExtra("to")}")
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "SMS SENT FEL: GENERIC_FAILURE till ${i.getStringExtra("to")}")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "SMS SENT FEL: NO_SERVICE")
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "SMS SENT FEL: NULL_PDU")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "SMS SENT FEL: RADIO_OFF")
                    }
                    else -> {
                        Log.e(TAG, "SMS SENT FEL: resultCode=$resultCode")
                    }
                }
                try {
                    ctx.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                    // Receiver redan avregistrerad
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT))
        }

        Log.d(TAG, "Skickar SMS till $from med reply=\"$reply\"")
        smsManager.sendTextMessage(from, null, reply, sentPendingIntent, null)

        val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
            putExtra("from", from)
            putExtra("body", body)
            putExtra("reply", reply)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // krävs från BroadcastReceiver
        }

        // Visa overlayn ovanpå allt, även om appen är i bakgrunden
        context.startActivity(overlayIntent)
    }
}