package com.lillans.image.blixtsvar

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Visa över låsskärm + väck skärmen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_overlay)

        // Hämta data från Intent
        val from  = intent.getStringExtra("from") ?: run { finish(); return }
        val body  = intent.getStringExtra("body") ?: ""
        val reply = intent.getStringExtra("reply") ?: ""

        val statusValue = findViewById<TextView>(R.id.statusValue)
        val messageBody = findViewById<TextView>(R.id.messageBody)
        val okButton    = findViewById<Button>(R.id.okButton)

        // Sätt rubrik
        val header = run {
            val idx = body.indexOf("-- BlixtSvar --")
            if (idx >= 0) {
                val after = body.substring(idx + "-- BlixtSvar --".length).trim()
                if (after.isNotEmpty()) "-- BlixtSvar -- $after" else "-- BlixtSvar --"
            } else {
                "-- BlixtSvar --"
            }
        } // ✅ detta stänger run-blocket korrekt!

        // Sätt statusfärg (JA = grön, NEJ = röd)
        val replyText = reply.trim()
        statusValue.text = replyText

        val upper = replyText.uppercase()
        when {
            upper == "JA"  -> statusValue.setTextColor(getColor(R.color.statusYes))
            upper == "NEJ" -> statusValue.setTextColor(getColor(R.color.statusNo))
            else           -> statusValue.setTextColor(getColor(R.color.overlayTitle)) // neutral
        }

        // Fyll meddelandetext
        val pretty = buildString {
            append(body.trim())
        }
        messageBody.text = pretty

        // OK stänger overlay
        okButton.setOnClickListener { finish() }
    } // ✅ stänger onCreate()

    override fun onBackPressed() {
        // Blockera "back" – användaren måste trycka OK
    }
}