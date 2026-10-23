package com.lillans.image.blixtsvar.tts

import android.app.Activity
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.slider.Slider
import com.lillans.image.blixtsvar.R
import java.util.Locale

/** Kopplar TTS-panelen i huvudvyn till [TtsSettings]. Rör inget i regel-/SMS-svarssystemet. */
class TtsPanelController(private val activity: Activity, root: View) {

    private val settings = TtsSettings(activity)

    private val swEnabled: SwitchCompat = root.findViewById(R.id.swTtsEnabled)
    private val spEngine: Spinner = root.findViewById(R.id.spTtsEngine)
    private val spVoice: Spinner = root.findViewById(R.id.spTtsVoice)
    private val tvRate: TextView = root.findViewById(R.id.tvTtsRate)
    private val slRate: Slider = root.findViewById(R.id.slTtsRate)

    private var engines: List<Pair<String, String>> = emptyList() // (paket, etikett)
    private var voices: List<Pair<String, String>> = emptyList() // (röstnamn, etikett)
    private var listing: TextToSpeech? = null

    fun bind() {
        swEnabled.isChecked = settings.enabled
        swEnabled.setOnCheckedChangeListener { _, checked -> settings.enabled = checked }

        slRate.valueFrom = TtsSettings.MIN_RATE
        slRate.valueTo = TtsSettings.MAX_RATE
        slRate.stepSize = 0.01f
        slRate.value = settings.rate
        showRate(settings.rate)
        slRate.addOnChangeListener { _, value, _ ->
            settings.rate = value
            showRate(value)
        }
        slRate.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) = speakSample()
        })

        loadEngines()
    }

    fun release() {
        listing?.shutdown()
        listing = null
    }

    private fun showRate(rate: Float) {
        tvRate.text = activity.getString(R.string.tts_rate_label, String.format(Locale.US, "%.2f", rate))
    }

    private fun loadEngines() {
        var probe: TextToSpeech? = null
        probe = TextToSpeech(activity) { status ->
            val tts = probe
            if (status != TextToSpeech.SUCCESS || tts == null) return@TextToSpeech
            engines = tts.engines.map { it.name to it.label }
            val default = tts.defaultEngine
            tts.shutdown()
            activity.runOnUiThread { fillEngines(default) }
        }
    }

    private fun fillEngines(defaultEngine: String?) {
        if (engines.isEmpty()) return
        spEngine.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item, engines.map { it.second }
        )
        val saved = settings.engine.ifEmpty { defaultEngine.orEmpty() }
        spEngine.setSelection(engines.indexOfFirst { it.first == saved }.coerceAtLeast(0), false)
        spEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val pkg = engines[pos].first
                if (pkg != settings.engine) {
                    settings.engine = pkg
                    settings.voice = ""
                }
                loadVoices()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        loadVoices()
    }

    private fun loadVoices() {
        listing?.shutdown()
        val engine = settings.engine.ifEmpty { null }
        var created: TextToSpeech? = null
        created = TextToSpeech(activity, { status ->
            val tts = created
            if (status != TextToSpeech.SUCCESS || tts == null) return@TextToSpeech
            val found = TtsVoices.localSwedish(tts).map { it.name to TtsVoices.label(it) }
            activity.runOnUiThread { fillVoices(found) }
        }, engine)
        listing = created
    }

    private fun fillVoices(found: List<Pair<String, String>>) {
        voices = found
        val labels = listOf(activity.getString(R.string.tts_voice_auto)) + found.map { it.second }
        spVoice.onItemSelectedListener = null
        spVoice.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
        val idx = voices.indexOfFirst { it.first == settings.voice }
        spVoice.setSelection(if (idx >= 0) idx + 1 else 0, false)
        spVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val name = if (pos == 0) "" else voices[pos - 1].first
                if (name != settings.voice) {
                    settings.voice = name
                    speakSample()
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun speakSample() {
        TtsSpeaker(activity, settings).speak(activity.getString(R.string.tts_sample))
    }
}
