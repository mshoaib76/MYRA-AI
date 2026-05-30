package com.myra.assistant.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.admin.AdminManager
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.util.AppLauncher
import com.myra.assistant.personalization.PersonalizationStore
import com.myra.assistant.util.NotificationListenerHelper
import com.myra.assistant.util.PrefsHelper
import com.myra.assistant.util.AuthManager
import com.myra.assistant.ui.auth.AuthActivity
import android.content.Intent

class SettingsActivity : AppCompatActivity() {

    private val models = listOf(
        "MYRA Voice (recommended)" to PrefsHelper.MODEL_NATIVE_AUDIO_LATEST
    )

    private val voices = listOf(
        "Aoede (Female)" to "Aoede",
        "Charon (Male)" to "Charon",
        "Kore (Female)" to "Kore",
        "Fenrir (Male)" to "Fenrir",
        "Puck (Male)" to "Puck",
        "Leda (Female)" to "Leda",
        "Orus (Male)" to "Orus",
        "Zephyr (Female)" to "Zephyr"
    )

    private val primeContacts = mutableListOf<PrimeContact>()
    private lateinit var primeAdapter: PrimeContactAdapter

    private val vaultEntries = mutableListOf<AdminManager.VaultEntry>()
    private lateinit var vaultAdapter: VaultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = PrefsHelper.prefs(this)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val userNameInput = findViewById<EditText>(R.id.userNameInput)
        val modelSpinner = findViewById<Spinner>(R.id.modelSpinner)
        val voiceSpinner = findViewById<Spinner>(R.id.voiceSpinner)
        val personalityGroup = findViewById<RadioGroup>(R.id.personalityGroup)
        val accessibilityStatus = findViewById<TextView>(R.id.accessibilityStatus)
        val adminPinInput = findViewById<EditText>(R.id.adminPinInput)
        val adminPatternInput = findViewById<EditText>(R.id.adminPatternInput)
        val callerNameAnnounceCheck = findViewById<CheckBox>(R.id.callerNameAnnounceCheck)
        val p = PersonalizationStore.prefs(this)

        apiKeyInput.setText(prefs.getString(PrefsHelper.KEY_API, ""))
        userNameInput.setText(PrefsHelper.getUserName(this))
        adminPatternInput.setText(AdminManager.getAdminPattern(this))
        callerNameAnnounceCheck.isChecked = PrefsHelper.shouldSpeakCallerName(this)

        modelSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, models.map { it.first }
        )
        voiceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, voices.map { it.first }
        )

        val savedModel = PrefsHelper.getModel(this)
        models.indexOfFirst { it.second == savedModel }.takeIf { it >= 0 }?.let {
            modelSpinner.setSelection(it)
        }
        voices.indexOfFirst { it.second == PrefsHelper.getVoice(this) }.takeIf { it >= 0 }?.let {
            voiceSpinner.setSelection(it)
        }

        when (PrefsHelper.getPersonality(this)) {
            PrefsHelper.PERSONALITY_PRO -> personalityGroup.check(R.id.personalityPro)
            PrefsHelper.PERSONALITY_ASSISTANT -> personalityGroup.check(R.id.personalityAssistant)
            else -> personalityGroup.check(R.id.personalityGf)
        }

        findViewById<EditText>(R.id.nicknameInput).setText(PersonalizationStore.getNickname(this))
        findViewById<EditText>(R.id.myraNicknameInput).setText(PersonalizationStore.getMyraNickname(this))
        findViewById<EditText>(R.id.cityInput).setText(PersonalizationStore.getCity(this))
        findViewById<EditText>(R.id.hobbiesInput).setText(PersonalizationStore.getHobbies(this))
        findViewById<EditText>(R.id.birthdayInput).setText(PersonalizationStore.getBirthday(this))
        findViewById<EditText>(R.id.memoriesInput).setText(
            PersonalizationStore.getMemories(this).joinToString("\n")
        )
        findViewById<SeekBar>(R.id.humorSeek).progress = PersonalizationStore.getHumorLevel(this)
        findViewById<SeekBar>(R.id.energySeek).progress = PersonalizationStore.getEnergyLevel(this)
        findViewById<SeekBar>(R.id.lengthSeek).progress = PersonalizationStore.getResponseLength(this)
        findViewById<TextView>(R.id.streakText).text =
            "🔥 Streak: ${PersonalizationStore.getStreak(this)} days · Sessions: ${PersonalizationStore.getTotalSessions(this)}"

        val langSpinner = findViewById<Spinner>(R.id.languageSpinner)
        val langs = listOf("Roman Urdu", "Hinglish", "English")
        langSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        val langIdx = when (PersonalizationStore.getLanguageStyle(this)) {
            PersonalizationStore.LANG_HINGLISH -> 1
            PersonalizationStore.LANG_ENGLISH -> 2
            else -> 0
        }
        langSpinner.setSelection(langIdx)

        primeContacts.addAll(PrefsHelper.getPrimeContacts(this))
        primeAdapter = PrimeContactAdapter(primeContacts) { index ->
            primeContacts.removeAt(index)
            primeAdapter.notifyDataSetChanged()
            updatePrimeHint(findViewById(R.id.primeContactsHint))
        }
        findViewById<RecyclerView>(R.id.primeContactsRecycler).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = primeAdapter
        }
        val waPrimeAlertsCheck = findViewById<CheckBox>(R.id.waPrimeAlertsCheck)
        val notificationAccessStatus = findViewById<TextView>(R.id.notificationAccessStatus)
        val primeHint = findViewById<TextView>(R.id.primeContactsHint)

        waPrimeAlertsCheck.isChecked = PrefsHelper.isWhatsAppPrimeAlertsEnabled(this)
        updateNotificationAccessStatus(notificationAccessStatus)
        notificationAccessStatus.setOnClickListener { NotificationListenerHelper.openSettings(this) }
        updatePrimeHint(primeHint)

        findViewById<Button>(R.id.addPrimeBtn).setOnClickListener { showAddPrimeDialog() }

        vaultEntries.addAll(AdminManager.getVault(this))
        vaultAdapter = VaultAdapter(vaultEntries) { index ->
            vaultEntries.removeAt(index)
            vaultAdapter.notifyDataSetChanged()
        }
        findViewById<RecyclerView>(R.id.vaultRecycler).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = vaultAdapter
        }
        findViewById<Button>(R.id.addVaultBtn).setOnClickListener { showAddVaultDialog() }

        updateAccessibilityStatus(accessibilityStatus)
        accessibilityStatus.setOnClickListener { AccessibilityHelperService.openSettings(this) }

        findViewById<Button>(R.id.testYoutubeBtn).setOnClickListener {
            AppLauncher.invalidateCache()
            AppLauncher.warmCache(this)
            Toast.makeText(this, AppLauncher.testOpenYoutube(this), Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.testWhatsappBtn).setOnClickListener {
            AppLauncher.invalidateCache()
            AppLauncher.warmCache(this)
            Toast.makeText(this, AppLauncher.testOpenWhatsapp(this), Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            val personality = when (personalityGroup.checkedRadioButtonId) {
                R.id.personalityPro -> PrefsHelper.PERSONALITY_PRO
                R.id.personalityAssistant -> PrefsHelper.PERSONALITY_ASSISTANT
                else -> PrefsHelper.PERSONALITY_GF
            }
            prefs.edit()
                .putString(PrefsHelper.KEY_API, apiKeyInput.text.toString().trim())
                .putString(PrefsHelper.KEY_USER_NAME, userNameInput.text.toString().trim())
                .putString(PrefsHelper.KEY_MODEL, models[modelSpinner.selectedItemPosition].second)
                .putString(PrefsHelper.KEY_VOICE, voices[voiceSpinner.selectedItemPosition].second)
                .putString(PrefsHelper.KEY_PERSONALITY, personality)
                .putBoolean(PrefsHelper.KEY_SPEAK_CALLER_NAME, callerNameAnnounceCheck.isChecked)
                .apply()
            PrefsHelper.setWhatsAppPrimeAlertsEnabled(this, waPrimeAlertsCheck.isChecked)
            NotificationListenerHelper.requestRebind(this)

            val pin = adminPinInput.text.toString().trim()
            if (pin.length >= 4) {
                AdminManager.setAdminPin(this, pin)
            }
            val pattern = adminPatternInput.text.toString().trim()
            if (pattern.isNotEmpty()) {
                AdminManager.setAdminPattern(this, pattern)
            }
            AdminManager.setAdminEnabled(this, pin.length >= 4 || pattern.isNotEmpty())

            PrefsHelper.savePrimeContacts(this, primeContacts)
            AdminManager.saveVault(this, vaultEntries)

            val lang = when (findViewById<Spinner>(R.id.languageSpinner).selectedItemPosition) {
                1 -> PersonalizationStore.LANG_HINGLISH
                2 -> PersonalizationStore.LANG_ENGLISH
                else -> PersonalizationStore.LANG_ROMAN_URDU
            }
            p.edit()
                .putString(PersonalizationStore.KEY_NICKNAME, findViewById<EditText>(R.id.nicknameInput).text.toString().trim())
                .putString(PersonalizationStore.KEY_MYRA_NICKNAME, findViewById<EditText>(R.id.myraNicknameInput).text.toString().trim())
                .putString(PersonalizationStore.KEY_CITY, findViewById<EditText>(R.id.cityInput).text.toString().trim())
                .putString(PersonalizationStore.KEY_HOBBIES, findViewById<EditText>(R.id.hobbiesInput).text.toString().trim())
                .putString(PersonalizationStore.KEY_BIRTHDAY, findViewById<EditText>(R.id.birthdayInput).text.toString().trim())
                .putString(PersonalizationStore.KEY_LANGUAGE, lang)
                .putInt(PersonalizationStore.KEY_HUMOR, findViewById<SeekBar>(R.id.humorSeek).progress)
                .putInt(PersonalizationStore.KEY_ENERGY, findViewById<SeekBar>(R.id.energySeek).progress)
                .putInt(PersonalizationStore.KEY_LENGTH, findViewById<SeekBar>(R.id.lengthSeek).progress)
                .apply()
            PersonalizationStore.saveMemories(
                this,
                findViewById<EditText>(R.id.memoriesInput).text.toString().lines()
            )

            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_LONG).show()
            finish()
        }

        findViewById<Button>(R.id.signOutBtn).setOnClickListener {
            AuthManager.signOut(this)
            val i = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(i)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus(findViewById(R.id.accessibilityStatus))
        updateNotificationAccessStatus(findViewById(R.id.notificationAccessStatus))
        updatePrimeHint(findViewById(R.id.primeContactsHint))
        primeAdapter.notifyDataSetChanged()
    }

    private fun updateAccessibilityStatus(tv: TextView) {
        if (AccessibilityHelperService.isEnabled(this)) {
            tv.text = getString(R.string.accessibility_on)
            tv.setTextColor(getColor(R.color.success))
        } else {
            tv.text = getString(R.string.accessibility_off)
            tv.setTextColor(getColor(R.color.primary_red))
        }
    }

    private fun updateNotificationAccessStatus(tv: TextView) {
        if (NotificationListenerHelper.isEnabled(this)) {
            tv.text = getString(R.string.notification_access_on)
            tv.setTextColor(getColor(R.color.success))
        } else {
            tv.text = getString(R.string.notification_access_off)
            tv.setTextColor(getColor(R.color.primary_red))
        }
    }

    private fun updatePrimeHint(tv: TextView) {
        tv.text = getString(
            R.string.prime_contacts_count,
            primeContacts.size,
            PrefsHelper.MAX_PRIME_CONTACTS
        )
        findViewById<Button>(R.id.addPrimeBtn).isEnabled =
            primeContacts.size < PrefsHelper.MAX_PRIME_CONTACTS
    }

    private fun showAddPrimeDialog() {
        if (primeContacts.size >= PrefsHelper.MAX_PRIME_CONTACTS) {
            Toast.makeText(this, R.string.prime_contacts_full, Toast.LENGTH_SHORT).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_add_prime_contact, null)
        AlertDialog.Builder(this)
            .setTitle("Add Prime Contact")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = view.findViewById<EditText>(R.id.dialogNameInput).text.toString().trim()
                val number = view.findViewById<EditText>(R.id.dialogNumberInput).text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    if (primeContacts.size >= PrefsHelper.MAX_PRIME_CONTACTS) {
                        Toast.makeText(this, R.string.prime_contacts_full, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    primeContacts.add(PrimeContact(name, number))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1)
                    updatePrimeHint(findViewById(R.id.primeContactsHint))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddVaultDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_vault, null)
        AlertDialog.Builder(this)
            .setTitle("Vault — saved lock password")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val label = view.findViewById<EditText>(R.id.vaultLabelInput).text.toString().trim()
                val secret = view.findViewById<EditText>(R.id.vaultSecretInput).text.toString().trim()
                val asPattern = view.findViewById<CheckBox>(R.id.vaultPatternCheck).isChecked
                if (label.isNotEmpty() && secret.isNotEmpty()) {
                    vaultEntries.add(AdminManager.VaultEntry(label, secret, asPattern))
                    vaultAdapter.notifyItemInserted(vaultEntries.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class PrimeContactAdapter(
    private val contacts: List<PrimeContact>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.primeItemName)
        val number: TextView = view.findViewById(R.id.primeItemNumber)
        val delete: ImageButton = view.findViewById(R.id.primeItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prime_contact, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = contacts[position]
        holder.name.text = c.name
        holder.number.text = c.number
        holder.delete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount(): Int = contacts.size
}

class VaultAdapter(
    private val entries: List<AdminManager.VaultEntry>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.vaultItemLabel)
        val hint: TextView = view.findViewById(R.id.vaultItemHint)
        val delete: ImageButton = view.findViewById(R.id.vaultItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_entry, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = entries[position]
        holder.label.text = e.label
        holder.hint.text = if (e.usePattern) "Pattern ••••" else "PIN/password ••••"
        holder.delete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount(): Int = entries.size
}
