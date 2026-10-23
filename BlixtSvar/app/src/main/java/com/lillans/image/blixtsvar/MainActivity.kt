package com.lillans.image.blixtsvar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lillans.image.blixtsvar.core.RulePresets
import com.lillans.image.blixtsvar.core.TriggerEngine
import com.lillans.image.blixtsvar.core.TriggerRule
import com.lillans.image.blixtsvar.data.RuleStore
import androidx.core.widget.doAfterTextChanged
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* valfritt: visa status */ }

    private lateinit var etPattern: EditText
    private lateinit var etReply: EditText
    private lateinit var btnPickStartTime: Button
    private lateinit var btnPickEndTime: Button
    private lateinit var etTimeReply: EditText

    private lateinit var cbLiteral: CheckBox
    private lateinit var cbIgnoreCase: CheckBox

    private lateinit var rgMatchType: RadioGroup
    private lateinit var tvMatchTypeLabel: TextView
    private lateinit var rbExactWord: RadioButton
    private lateinit var rbWordStart: RadioButton
    private lateinit var rbAnywhere: RadioButton
    private lateinit var tvMatchTypeHint: TextView

    private lateinit var btnAddOrUpdate: Button
    private lateinit var btnCreateTimeRule: Button

    private lateinit var etTestMsg: EditText
    private lateinit var btnTest: Button
    private lateinit var tvTestResult: TextView

    private lateinit var rulesContainer: LinearLayout
    private lateinit var btnHelp: Button

    // Nya knappar för driftsäkerhet
    private lateinit var btnBatterySettings: Button
    private lateinit var btnAutostartSettings: Button
    private lateinit var btnLockRecentsHelp: Button

    private lateinit var store: RuleStore
    private lateinit var etIgnoredText: EditText
    private lateinit var btnSaveIgnored: Button
    private lateinit var tvIgnoredStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ensureNotificationPermission()
        startKeepAliveService()
        requestIgnoreBatteryOptimizationsIfNeeded()
        ensureSmsPermissions()

        store = RuleStore(this)

        etPattern = findViewById(R.id.etPattern)
        etReply = findViewById(R.id.etReply)
        btnPickStartTime = findViewById(R.id.btnPickStartTime)
        btnPickEndTime = findViewById(R.id.btnPickEndTime)
        etTimeReply = findViewById(R.id.etTimeReply)
        cbLiteral = findViewById(R.id.cbLiteral)
        cbIgnoreCase = findViewById(R.id.cbIgnoreCase)

        rgMatchType = findViewById(R.id.rgMatchType)
        tvMatchTypeLabel = findViewById(R.id.tvMatchTypeLabel)
        rbExactWord = findViewById(R.id.rbExactWord)
        rbWordStart = findViewById(R.id.rbWordStart)
        rbAnywhere = findViewById(R.id.rbAnywhere)
        tvMatchTypeHint = findViewById(R.id.tvMatchTypeHint)

        btnAddOrUpdate = findViewById(R.id.btnAddOrUpdate)
        btnCreateTimeRule = findViewById(R.id.btnCreateTimeRule)

        etTestMsg = findViewById(R.id.etTestMsg)
        btnTest = findViewById(R.id.btnTest)
        tvTestResult = findViewById(R.id.tvTestResult)
        tvTestResult.movementMethod = ScrollingMovementMethod()

        if (savedInstanceState == null) {
            updatePickedTimeButton(btnPickStartTime, "21:00")
            updatePickedTimeButton(btnPickEndTime, "07:00")
            etTimeReply.setText("JA")
        }

        rulesContainer = findViewById(R.id.rulesContainer)
        btnHelp = findViewById(R.id.btnHelp)

        // Dessa id:n måste finnas i activity_main.xml
        btnBatterySettings = findViewById(R.id.btnBatterySettings)
        btnAutostartSettings = findViewById(R.id.btnAutostartSettings)
        btnLockRecentsHelp = findViewById(R.id.btnLockRecentsHelp)

        btnAddOrUpdate.setOnClickListener { addOrUpdateRule() }
        btnCreateTimeRule.setOnClickListener { createTimeRule() }
        btnTest.setOnClickListener { runTest() }
        btnHelp.setOnClickListener { showHelpDialog() }
        cbLiteral.setOnCheckedChangeListener { _, _ -> updatePatternModeUi() }
        btnPickStartTime.setOnClickListener { showTimePicker(btnPickStartTime) }
        btnPickEndTime.setOnClickListener { showTimePicker(btnPickEndTime) }

        btnBatterySettings.setOnClickListener { openBatterySettings() }
        btnAutostartSettings.setOnClickListener { openAutoStartSettings() }
        btnLockRecentsHelp.setOnClickListener { showLockInRecentsHelpDialog() }

        etIgnoredText = findViewById(R.id.etIgnoredText)
        btnSaveIgnored = findViewById(R.id.btnSaveIgnored)
        tvIgnoredStatus = findViewById(R.id.tvIgnoredStatus)
        etIgnoredText.setText(store.loadIgnoredText())
        btnSaveIgnored.setOnClickListener { saveIgnoredText() }
        etIgnoredText.doAfterTextChanged { saveIgnoredText() }

        updatePatternModeUi()
        ensureDefaultsSeededOnce()
        renderRules()
    }

    private val defaults = RulePresets.defaultRules

    private fun ensureDefaultsSeededOnce() {
        val prefs = getSharedPreferences("rules_seed", MODE_PRIVATE)

        val SEED_VERSION = 3
        val current = prefs.getInt("seed_version", 0)
        if (current < SEED_VERSION) {
            val existing = store.load()
            if (existing.isEmpty()) {
                store.save(defaults)
            }
            prefs.edit().putInt("seed_version", SEED_VERSION).apply()
        }

        val BACKFILL_KEY = "backfill_time_rules_v3_done"
        if (!prefs.getBoolean(BACKFILL_KEY, false)) {
            val list = store.load().toMutableList()
            var changed = false

            list.indices.forEach { index ->
                val existingRule = list[index]
                val migratedRule = RulePresets.migrateStoredTimeRule(existingRule)
                if (migratedRule.id != existingRule.id ||
                    migratedRule.pattern.pattern != existingRule.pattern.pattern ||
                    migratedRule.uid != existingRule.uid
                ) {
                    list[index] = migratedRule
                    changed = true
                }
            }

            if (changed) {
                store.save(list)
            }
            prefs.edit().putBoolean(BACKFILL_KEY, true).apply()
        }
    }

    private fun updatePatternModeUi() {
        val literalMode = cbLiteral.isChecked
        rbExactWord.isEnabled = literalMode
        rbWordStart.isEnabled = literalMode
        rbAnywhere.isEnabled = literalMode
        tvMatchTypeHint.text = if (literalMode) {
            "Välj hur den vanliga frasen ska hittas i SMS:et."
        } else {
            "Matchtyp används bara för vanliga fraser. I regex-läge används mönstret exakt som du skriver det."
        }
        tvMatchTypeHint.alpha = if (literalMode) 1f else 0.7f
        tvMatchTypeLabel.visibility = if (literalMode) View.VISIBLE else View.GONE
        rgMatchType.visibility = if (literalMode) View.VISIBLE else View.GONE
    }

    private fun createTimeRule() {
        val startTime = RulePresets.normalizeTimeInput(
            btnPickStartTime.text.toString()
        )

        if (startTime == null) {
            toast("Ange starttid som HH:MM")
            return
        }

        val endTime = RulePresets.normalizeTimeInput(
            btnPickEndTime.text.toString()
        )

        if (endTime == null) {
            toast("Ange sluttid som HH:MM")
            return
        }

        val reply = etTimeReply.text.toString().trim()

        if (reply.isEmpty()) {
            toast("Skriv vilket svar som ska skickas")
            etTimeReply.requestFocus()
            return
        }

        val newRule = RulePresets.buildTimeRule(
            id = RulePresets.suggestedRuleId(startTime, endTime),
            startTime = startTime,
            endTime = endTime,
            reply = reply
        )

        store.save(
            RulePresets.upsertTimeRule(
                store.load(),
                newRule
            )
        )

        renderRules()

        updatePickedTimeButton(btnPickStartTime, startTime)
        updatePickedTimeButton(btnPickEndTime, endTime)

        toast("Tidsregel sparad")
    }

    private fun showTimePicker(targetButton: Button) {
        val normalized = RulePresets.normalizeTimeInput(targetButton.text.toString()) ?: "00:00"
        val (hourText, minuteText) = normalized.split(":")
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                updatePickedTimeButton(
                    targetButton,
                    String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                )
            },
            hourText.toInt(),
            minuteText.toInt(),
            true
        ).show()
    }

    private fun updatePickedTimeButton(targetButton: Button, time: String) {
        targetButton.text = time
        targetButton.contentDescription = when (targetButton.id) {
            R.id.btnPickStartTime -> "Starttid $time, öppna tidsväljare"
            R.id.btnPickEndTime -> "Sluttid $time, öppna tidsväljare"
            else -> time
        }
    }

    private fun addOrUpdateRule() {
        val patternInput = etPattern.text.toString().trim()
        val replyInput = etReply.text.toString().trim()

        if (patternInput.isEmpty()) {
            toast("Skriv en fras eller regex")
            return
        }
        if (replyInput.isEmpty()) {
            toast("Skriv en svarstext")
            return
        }

        val options = mutableSetOf<RegexOption>()
        if (cbIgnoreCase.isChecked) options += RegexOption.IGNORE_CASE

        if (!cbLiteral.isChecked) {
            options += RegexOption.DOT_MATCHES_ALL
        }

        val regex = if (cbLiteral.isChecked) {
            val escaped = Regex.escape(patternInput)
            when {
                rbExactWord.isChecked -> Regex("\\b$escaped\\b", options)
                rbWordStart.isChecked -> Regex("\\b$escaped", options)
                else -> Regex(escaped, options)
            }
        } else {
            val patternForStore = if (patternInput.startsWith("(?s)")) patternInput else "(?s)$patternInput"
            Regex(patternForStore, options)
        }

        val rules = store.load().toMutableList()
        val existing = rules.indexOfFirst { it.pattern.pattern == regex.pattern }
        if (existing >= 0) {
            rules[existing] = rules[existing].copy(reply = replyInput)
        } else {
            val id = "r_${System.currentTimeMillis()}"
            rules += TriggerRule(id, regex, replyInput, true)
        }

        store.save(rules)
        renderRules()
        toast("Regel sparad")
    }

    private fun saveIgnoredText() {
        store.saveIgnoredText(etIgnoredText.text.toString())
        tvIgnoredStatus.text = getString(R.string.ignored_text_saved)
    }

    override fun onPause() {
        super.onPause()
        saveIgnoredText()
    }

    private fun runTest() {
        val msg = etTestMsg.text.toString()
        val engine = TriggerEngine(store.load(), etIgnoredText.text.toString())
        val hit = engine.match(msg)
        tvTestResult.text = if (engine.isIgnoredRaw(msg)) {
            "Ignorerad: SMS innehåller ignorera-texten."
        } else if (hit == null) {
            "Ingen regel matchade."
        } else {
            "Match! Autosvar skulle bli:\n\"$hit\""
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPerms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    private fun startKeepAliveService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val id = "keepalive_channel"
            if (nm.getNotificationChannel(id) == null) {
                val ch = NotificationChannel(
                    id, "BlixtSvar - Bakgrundsdrift", NotificationManager.IMPORTANCE_MIN
                )
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
        }

        val intent = Intent(this, com.lillans.image.blixtsvar.keepalive.KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestIgnoreBatteryOptimizationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val pkg = packageName
            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$pkg")
                    startActivity(i)
                } catch (_: Exception) {
                    // best effort
                }
            }
        }
    }

    private fun openBatterySettings() {
        try {
            val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(i)
            return
        } catch (_: Exception) {
        }

        openAppDetailsSettings()
    }

    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (_: Exception) {
            toast("Kunde inte öppna appinställningar")
        }
    }

    private fun openAutoStartSettings() {
        val intents = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        )

        var opened = false
        for (intent in intents) {
            val ok = runCatching {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    true
                } else false
            }.getOrDefault(false)

            if (ok) {
                opened = true
                break
            }
        }

        if (!opened) {
            openAppDetailsSettings()
            showAutoStartHelpDialog()
        }
    }

    private fun showAutoStartHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tillåt autostart")
            .setMessage(
                "Aktivera autostart för BlixtSvar i telefonens batteri-/apphantering.\n\n" +
                        "Vanliga namn:\n" +
                        "• Autostart\n" +
                        "• Starta i bakgrunden\n" +
                        "• Tillåt bakgrundsaktivitet\n\n" +
                        "Om du inte hittar inställningen: sök efter 'autostart' i Inställningar."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLockInRecentsHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Lås app i Senaste appar")
            .setMessage(
                "Öppna Senaste appar, hitta BlixtSvar och lås appen (hänglås/pin beroende på mobil).\n\n" +
                        "Detta minskar risken att systemet stänger appen i bakgrunden."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderRules() {
        rulesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val rules = store.load()

        if (rules.isEmpty()) {
            val t = TextView(this)
            t.text = "Inga regler ännu."
            rulesContainer.addView(t)
            return
        }

        rules.forEach { rule ->
            val row = inflater.inflate(R.layout.item_rule, rulesContainer, false)
            val ruleUid = rule.uid
            val displayName = RulePresets.displayNameFor(rule)

            row.findViewById<TextView>(R.id.tvRuleText).text =
                "- $displayName | /${rule.pattern.pattern}/ → \"${rule.reply}\" " +
                        if (rule.enabled) "(aktiv)" else "(inaktiv)"

            row.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                val updated = store.load().toMutableList()
                val targetIndex = updated.indexOfFirst { it.uid == ruleUid }
                if (targetIndex < 0) return@setOnClickListener
                updated.removeAt(targetIndex)
                store.save(updated)
                renderRules()
            }

            row.findViewById<Button>(R.id.btnLoadToEditor).setOnClickListener {
                etPattern.setText(rule.pattern.pattern)
                etReply.setText(rule.reply)
                cbLiteral.isChecked = false
                when {
                    rule.pattern.pattern.startsWith("\\b") &&
                            rule.pattern.pattern.endsWith("\\b") -> rbExactWord.isChecked = true
                    rule.pattern.pattern.startsWith("\\b") -> rbWordStart.isChecked = true
                    else -> rbAnywhere.isChecked = true
                }
            }

            val cb = row.findViewById<CheckBox>(R.id.cbEnabled)
            cb.setOnCheckedChangeListener(null)
            cb.isChecked = rule.enabled
            cb.setOnCheckedChangeListener { _, checked ->
                val updated = store.load().toMutableList()
                val targetIndex = updated.indexOfFirst { it.uid == ruleUid }
                if (targetIndex < 0) return@setOnCheckedChangeListener
                updated[targetIndex] = updated[targetIndex].copy(enabled = checked)
                store.save(updated)
                val updatedRule = updated[targetIndex]
                row.findViewById<TextView>(R.id.tvRuleText).text =
                    "- ${RulePresets.displayNameFor(updatedRule)} | /${updatedRule.pattern.pattern}/ → \"${updatedRule.reply}\" " +
                            if (checked) "(aktiv)" else "(inaktiv)"
            }

            rulesContainer.addView(row)
        }
    }

    private fun showHelpDialog() {
        val message = """
Snabbast för arbetspass:

1. Fyll i starttid och sluttid under "Skapa tidsregel".
2. Skriv själv vilket svar som ska skickas.
3. Tryck på "Skapa tidsregel".

Exempel:
- 21:00 till 07:00 = natt, svar: JA
- 07:00 till 16:00 = dag, svar: Jag kan jobba
- 14:00 till 21:00 = kväll, svar: Nej, jag har inte tid

Privat exempel:
- Fras: Ring mig
- Svar: Nej, inte just nu. Jag är upptagen.
- Markera: "Vanlig fras (ingen regex)"
- Tryck: "Spara avancerad regel"

Det betyder att du alltid kan välja exakt vilket svar som ska skickas.
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hjälp")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun ensureSmsPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPerms.launch(needed.toTypedArray())
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}