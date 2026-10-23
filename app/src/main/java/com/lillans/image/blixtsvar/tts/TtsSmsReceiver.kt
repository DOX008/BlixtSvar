package com.lillans.image.blixtsvar.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log

/**
 * Läser upp inkommande SMS. Helt fristående från sms.SmsReceiver (autosvar):
 * delar varken regler, throttling eller någon annan logik med den.
 */
class TtsSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val settings = TtsSettings(context)
        if (!settings.enabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val from = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }.trim()
        if (body.isEmpty()) return

        val text = if (from.isNotBlank()) "SMS från $from. $body" else "SMS. $body"
        Log.d(TAG, "Läser upp inkommande SMS")

        val pending = goAsync()
        val speaker = TtsSpeaker(context, settings)
        val handler = Handler(Looper.getMainLooper())
        var released = false
        fun release() {
            if (!released) {
                released = true
                pending.finish()
            }
        }
        // Systemet tillåter bara ~10 s för broadcasten; uppläsningen får fortsätta
        // (processen hålls vid liv av KeepAliveService) men en säkerhetsgräns stänger den.
        handler.postDelayed({ release() }, RELEASE_MS)
        handler.postDelayed({ speaker.cancel() }, MAX_SPEECH_MS)
        speaker.speak(text) {
            handler.removeCallbacksAndMessages(null)
            release()
        }
    }

    companion object {
        private const val TAG = "BlixtSvarTTS"
        private const val RELEASE_MS = 8_000L
        private const val MAX_SPEECH_MS = 180_000L
    }
}
