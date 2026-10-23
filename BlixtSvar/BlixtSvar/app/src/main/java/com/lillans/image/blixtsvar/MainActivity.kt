package com.lillans.image.blixtsvar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lillans.image.blixtsvar.core.TriggerEngine
import com.lillans.image.blixtsvar.core.TriggerRule
import com.lillans.image.blixtsvar.data.RuleStore
import android.app.NotificationChannel
import android.app.NotificationManager

class MainActivity : AppCompatActivity() {

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* valfritt: visa status */ }

    private lateinit var etPattern: EditText
    private lateinit var etReply: EditText
    private lateinit var cbLiteral: CheckBox
    private lateinit var cbIgnoreCase: CheckBox

    private lateinit var rgMatchType: RadioGroup
    private lateinit var rbExactWord: RadioButton
    private lateinit var rbWordStart: RadioButton
    private lateinit var rbAnywhere: RadioButton

    private lateinit var btnAddOrUpdate: Button

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
        cbLiteral = findViewById(R.id.cbLiteral)
        cbIgnoreCase = findViewById(R.id.cbIgnoreCase)

        rgMatchType = findViewById(R.id.rgMatchType)
        rbExactWord = findViewById(R.id.rbExactWord)
        rbWordStart = findViewById(R.id.rbWordStart)
        rbAnywhere = findViewById(R.id.rbAnywhere)

        btnAddOrUpdate = findViewById(R.id.btnAddOrUpdate)

        etTestMsg = findViewById(R.id.etTestMsg)
        btnTest = findViewById(R.id.btnTest)
        tvTestResult = findViewById(R.id.tvTestResult)
        tvTestResult.movementMethod = ScrollingMovementMethod()

        rulesContainer = findViewById(R.id.rulesContainer)
        btnHelp = findViewById(R.id.btnHelp)

        // Dessa id:n måste finnas i activity_main.xml
        btnBatterySettings = findViewById(R.id.btnBatterySettings)
        btnAutostartSettings = findViewById(R.id.btnAutostartSettings)
        btnLockRecentsHelp = findViewById(R.id.btnLockRecentsHelp)

        btnAddOrUpdate.setOnClickListener { addOrUpdateRule() }
        btnTest.setOnClickListener { runTest() }
        btnHelp.setOnClickListener { showHelpDialog() }

        // Nya klicklyssnare
        btnBatterySettings.setOnClickListener { openBatterySettings() }
        btnAutostartSettings.setOnClickListener { openAutoStartSettings() }
        btnLockRecentsHelp.setOnClickListener { showLockInRecentsHelpDialog() }

        ensureDefaultsSeededOnce()
        renderRules()
    }

    private val defaults = listOf(
        TriggerRule(
            "hej",
            Regex("\\b(hej|hello|tja|tjena)\\b", RegexOption.IGNORE_CASE),
            "Hej! [BlixtSvar]"
        ),
        TriggerRule(
            "saldo",
            Regex("\\bsaldo\\b", RegexOption.IGNORE_CASE),
            "Ditt saldo är 123 kr. [BlixtSvar]"
        ),
        TriggerRule(
            "order",
            Regex("\\border\\b", RegexOption.IGNORE_CASE),
            "Din order är mottagen. [BlixtSvar]"
        ),
        TriggerRule(
            "Dag",
            Regex("(?s)(?=.*0?7:00)(?=.*16:00)", RegexOption.IGNORE_CASE),
            "JA"
        ),
        TriggerRule(
            "Kvall",
            Regex("(?s)(?=.*14:00)(?=.*21:00)", RegexOption.IGNORE_CASE),
            "JA"
        ),
        TriggerRule(
            "Natt",
            Regex("(?s)(?=.*20:55)(?=.*0?6:55)", RegexOption.IGNORE_CASE),
            "JA"
        )
    )

    private fun ensureDefaultsSeededOnce() {
        val prefs = getSharedPreferences("rules_seed", MODE_PRIVATE)

        // 1) Ordinarie "seed once" om helt tom lista (förhindrar att raderade regler kommer tillbaka)
        val SEED_VERSION = 2
        val current = prefs.getInt("seed_version", 0)
        if (current < SEED_VERSION) {
            val existing = store.load()
            if (existing.isEmpty()) {
                store.save(defaults)
            }
            prefs.edit().putInt("seed_version", SEED_VERSION).apply()
        }

        // 2) Engångs-backfill: lägg tillbaka Dag/Kvall/Natt om de saknas (och bara en gång)
        val BACKFILL_KEY = "backfill_time_rules_v1_done"
        if (!prefs.getBoolean(BACKFILL_KEY, false)) {
            val list = store.load().toMutableList()

            val haveDag   = list.any { it.id == "Dag" }
            val haveKvall = list.any { it.id == "Kvall" }
            val haveNatt  = list.any { it.id == "Natt" }

            val add = mutableListOf<TriggerRule>()
            if (!haveDag)   add += defaults.first { it.id == "Dag" }
            if (!haveKvall) add += defaults.first { it.id == "Kvall" }
            if (!haveNatt)  add += defaults.first { it.id == "Natt" }

            if (add.isNotEmpty()) {
                store.save(list + add)
            }
            prefs.edit().putBoolean(BACKFILL_KEY, true).apply()
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
        options += RegexOption.DOT_MATCHES_ALL

        // Låt . matcha över radbrytningar för icke-litterala regexar
        if (!cbLiteral.isChecked) {
            options += RegexOption.DOT_MATCHES_ALL
        }

        val regex = if (cbLiteral.isChecked) {
            val escaped = Regex.escape(patternInput)
            when {
                rbExactWord.isChecked   -> Regex("\\b$escaped\\b", options)
                rbWordStart.isChecked   -> Regex("\\b$escaped", options)
                else                    -> Regex(escaped, options)
            }
        } else {
            // Inbygg (?s) i mönstret om det saknas, så det bevaras vid lagring/återladdning
            val patternForStore = if (patternInput.startsWith("(?s)")) patternInput else "(?s)$patternInput"
            Regex(patternForStore, options)
        }

        val rules = store.load().toMutableList()
        val existing = rules.indexOfFirst { it.pattern.pattern == regex.pattern }
        if (existing >= 0) {
            rules[existing] = rules[existing].copy(reply = replyInput)  // behåll enabled
        } else {
            val id = "r_${System.currentTimeMillis()}"
            rules += TriggerRule(id, regex, replyInput, true)
        }

        store.save(rules)
        renderRules()
        toast("Regel sparad")
    }

    private fun runTest() {
        val msg = etTestMsg.text.toString()
        val engine = TriggerEngine(store.load())
        val hit = engine.match(msg)
        tvTestResult.text = if (hit == null) {
            "Ingen regel matchade."
        } else {
            "Match! Autosvar skulle bli:\n\"$hit\""
        }
    }

    private fun ensureNotificationPermission() {
        // Android 13+ behöver POST_NOTIFICATIONS runtime
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
        // starta notiskanal (om inte redan skapad)
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

    // ===== NYTT: "Unrestricted battery" =====
    private fun openBatterySettings() {
        // Försök först öppna batteri-optimeringslistan
        try {
            val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(i)
            return
        } catch (_: Exception) {
        }

        // Fallback: appens infosida
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

    // ===== NYTT: "Tillåt autostart" =====
    private fun openAutoStartSettings() {
        val intents = listOf(
            // Xiaomi
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Oppo
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            // Vivo
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // Huawei
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

    // ===== NYTT: "Lås app i recent apps" =====
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

            row.findViewById<TextView>(R.id.tvRuleText).text =
                "- ${rule.id} | /${rule.pattern.pattern}/ → \"${rule.reply}\" " +
                        if (rule.enabled) "(aktiv)" else "(inaktiv)"

            row.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                val newList = store.load().filterNot { it.id == rule.id }
                store.save(newList)
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
            cb.isChecked = rule.enabled
            cb.setOnCheckedChangeListener { _, checked ->
                val updated = store.load().map { existing ->
                    if (existing.id == rule.id) existing.copy(enabled = checked) else existing
                }
                store.save(updated)
                row.findViewById<TextView>(R.id.tvRuleText).text =
                    "- ${rule.id} | /${rule.pattern.pattern}/ → \"${rule.reply}\" " +
                            if (checked) "(aktiv)" else "(inaktiv)"
            }

            rulesContainer.addView(row)
        }
    }

    private fun showHelpDialog() {
        val message = """
🧠 Grunderna

En regex (regular expression / reguljärt uttryck) är ett sätt att beskriva mönster i text.
Appen använder regex för att känna igen ord eller fraser i inkommande SMS.

🧩 Exempel:
\b(hej|hello|tja|tjena)\b

Delar:
- \b = Ordsgräns – markerar början/slut av ett ord.
- (hej|tja|tjena) =

✅ Matchar: "hej", "tja", "tjena"
❌ Matchar inte: "hejsan", "tjenare" m.m.

🧩 Fler exempel:
- \bsaldo\b → matchar endast ordet 'saldo' som eget ord.
- hej → matchar överallt (även i 'hejsan').
- ^OK → matchar text som börjar med "OK".
- hej$ → matchar text som slutar med "hej".
- [0-9]+ → matchar bara om meddelandet innehåller siffror.

💡Tips:
Vikarier och SMS-förfrågningar..
Om du vill svara 'JA' till jobb på specifika tider, exempelvis nattjobb:

En förfrågan kan se ut så här:

↙️✉️ Inkommande SMS
──────────────
  +467090320040
──────────────
Förfrågan ID 78278: Nattpatrullen
Nygatan 32
14 nov 20:55 - 15 nov 6:55
15 nov 20:55 - 16 nov 6:55
(Svara på detta SMS. Svara med JA eller NEJ för automatiskt behandling.

Korrekt format blir då:
(?s)(?=.*20:55)(?=.*6:55)

Du kommer nu att automatiskt svara 'JA' på alla sms med nattpass.

Eller "JA" till alla kvällspass:
(?s)(?=.*14:00)(?=.*21:00)

(?=.*14:00) kräver att smset innehåller “14:00” någonstans i texten
(?=.*21:00) kräver att smset innehåller “21:00” någonstans i texten

Tillsammans betyder detta: det inkommande SMS:et MÅSTE innehålla båda tiderna i samma meddelande, för att kunna skicka ett "JA".
Detta förhindrar att din mobil skickar okontrollerat sms till fel mottagare.

--- Appen skapad av:
Alexander Andreassen
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Regex-hjälp")
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