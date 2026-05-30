package com.myra.assistant.ui.main

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.myra.assistant.ui.usage.UsageFragment
import com.myra.assistant.ui.history.HistoryFragment
import com.myra.assistant.ui.profile.ProfileFragment
import com.myra.assistant.util.CommandHistoryFormatter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.myra.assistant.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.model.ChatMessage
import com.myra.assistant.call.PendingIncomingCall
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.util.CallActionHelper
import com.myra.assistant.util.CallerInfo
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.util.AppLauncher
import com.myra.assistant.util.OverlayPermissionHelper
import com.myra.assistant.util.PrefsHelper
import com.myra.assistant.db.HistoryDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.myra.assistant.viewmodel.MainViewModel
import com.myra.assistant.personalization.PersonalizationStore
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.ui.auth.AuthActivity
import com.myra.assistant.util.AuthManager
import com.myra.assistant.util.NotificationListenerHelper
import com.myra.assistant.whatsapp.PendingWhatsAppInbox
import com.myra.assistant.whatsapp.WhatsAppInboxCommandParser
import com.myra.assistant.whatsapp.WhatsAppPrimeReplyHelper
import com.myra.assistant.util.CallSpeaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var geminiLive: GeminiLiveClient
    private lateinit var audioEngine: AudioEngine
    private val chatAdapter = ChatAdapter()

    private lateinit var orbView: OrbAnimationView
    private lateinit var waveformView: WaveformView
    private lateinit var statusText: TextView
    private lateinit var redOverlay: View
    private lateinit var micButton: ImageButton
    private lateinit var batteryText: TextView
    private lateinit var ramText: TextView
    private lateinit var timeText: TextView
    private lateinit var homeContainer: View
    private lateinit var fragmentContainer: View
    private lateinit var bottomNavigation: BottomNavigationView

    private var inputBuffer = StringBuilder()
    private var outputBuffer = StringBuilder()
    private var isMuted = false
    private var isActiveMode = false
    private var isInCallMode = false
    private var geminiInitialized = false
    private var greetingSent = false
    private var lastExecutedCommandKey = ""
    private lateinit var historyDb: HistoryDbHelper
    private var waInboxSpeaker: CallSpeaker? = null

    private val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishIncomingCallMode()
        }
    }

    private val incomingCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                CallMonitorService.ACTION_INCOMING_CALL -> {
                    val caller = CallerInfo(
                        displayName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME).orEmpty(),
                        phoneNumber = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NUMBER).orEmpty(),
                        isSavedContact = intent.getBooleanExtra(CallMonitorService.EXTRA_IS_CONTACT, false)
                    )
                    handleIncomingPhoneCall(caller)
                }
                ACTION_CALL_USER_DECISION -> {
                    val decision = intent.getStringExtra(EXTRA_DECISION) ?: return
                    applyCallDecision(decision)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isSignedIn(this)) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        initViews()
        checkPermissions()
        OverlayPermissionHelper.requestIfNeeded(this)
        startSystemServices()
        startStatusUpdates()
        val callFilter = IntentFilter().apply {
            addAction(CallMonitorService.ACTION_CALL_ENDED)
            addAction(CallMonitorService.ACTION_INCOMING_CALL)
            addAction(ACTION_CALL_USER_DECISION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED), RECEIVER_NOT_EXPORTED)
            registerReceiver(incomingCallReceiver, callFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(callEndedReceiver, IntentFilter(CallMonitorService.ACTION_CALL_ENDED))
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(incomingCallReceiver, callFilter)
        }
        tryStartMyra()
        maybeHandleIncomingFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleIncomingFromIntent(intent)
    }

    private fun initViews() {
        orbView = findViewById(R.id.orbView)
        waveformView = findViewById(R.id.waveformView)
        statusText = findViewById(R.id.statusText)
        redOverlay = findViewById(R.id.redOverlay)
        micButton = findViewById(R.id.micButton)
        batteryText = findViewById(R.id.batteryText)
        ramText = findViewById(R.id.ramText)
        timeText = findViewById(R.id.timeText)
        homeContainer = findViewById(R.id.homeContainer)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        historyDb = HistoryDbHelper(this)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    homeContainer.visibility = View.VISIBLE
                    fragmentContainer.visibility = View.GONE
                    true
                }
                R.id.nav_usage -> {
                    homeContainer.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    loadFragment(UsageFragment())
                    true
                }
                R.id.nav_history -> {
                    homeContainer.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    homeContainer.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        val chatRecycler = findViewById<RecyclerView>(R.id.chatRecycler)
        chatRecycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecycler.adapter = chatAdapter

        findViewById<View>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        micButton.setOnClickListener { toggleMute() }
        micButton.setOnLongClickListener {
            if (::audioEngine.isInitialized) audioEngine.clearPlaybackQueue()
            if (::geminiLive.isInitialized) geminiLive.sendInterrupt()
            true
        }

        orbView.setOnClickListener {
            if (!::geminiLive.isInitialized) tryStartMyra()
            else if (!isMuted) statusText.text = "Sun rahi hoon..."
        }

        orbView.setState(OrbState.IDLE)
        startHeaderPulseAnimation()
    }

    private fun startHeaderPulseAnimation() {
        val title = findViewById<TextView>(R.id.titleMyra)
        val pulse = ObjectAnimator.ofFloat(title, View.ALPHA, 1f, 0.65f, 1f).apply {
            duration = 2200
            repeatCount = ObjectAnimator.INFINITE
        }
        pulse.start()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun tryStartMyra() {
        if (!hasMicPermission()) {
            statusText.text = "Mic permission required"
            checkPermissions()
            return
        }
        if (PrefsHelper.getApiKey(this).isBlank()) {
            statusText.text = "Add API key in Settings"
            Toast.makeText(this, "Set Gemini API key in Settings", Toast.LENGTH_LONG).show()
            return
        }
        if (geminiInitialized) return
        handler.postDelayed({ initGeminiLive() }, 300)
    }

    private fun initGeminiLive() {
        if (geminiInitialized) return

        geminiLive = GeminiLiveClient(this)
        audioEngine = AudioEngine(this)
        geminiInitialized = true

        audioEngine.onRecordError = { msg ->
            runOnUiThread {
                statusText.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        geminiLive.onConnected = {
            runOnUiThread { statusText.text = "Connecting to Gemini..." }
        }

        geminiLive.onStatus = { msg ->
            runOnUiThread { statusText.text = msg }
        }

        geminiLive.onSetupComplete = {
            runOnUiThread {
                lifecycleScope.launch(Dispatchers.IO) {
                    AppLauncher.warmCache(this@MainActivity)
                }
                val micOk = audioEngine.startRecording()
                val spkOk = audioEngine.startPlayback()
                if (!micOk || !spkOk) {
                    statusText.text = "Audio error — check mic permission"
                    return@runOnUiThread
                }
                audioEngine.setMuted(isMuted)
                setActiveMode(true)
                orbView.setState(OrbState.LISTENING)
                waveformView.startAnimation()
                if (isInCallMode) {
                    announceIncomingCallToAgent()
                } else {
                    statusText.text = "Sun rahi hoon... (bol: YouTube kholo)"
                    if (!greetingSent) {
                        greetingSent = true
                        handler.postDelayed({ sendGreeting() }, 600)
                    }
                }
            }
        }

        geminiLive.onAudioReceived = { pcm, sampleRate ->
            audioEngine.setPlaybackSampleRate(sampleRate)
            audioEngine.queueAudio(pcm)
        }

        geminiLive.onInputTranscript = { text ->
            runOnUiThread {
                inputBuffer.append(text)
                val partial = inputBuffer.toString()
                if (isInCallMode && tryHandleCallDecision(partial)) {
                    inputBuffer = StringBuilder()
                    return@runOnUiThread
                }
                if (CommandParser.isCreatorQuestion(partial)) {
                    geminiLive.sendText(CommandParser.CREATOR_ANSWER)
                    chatAdapter.addMessage(
                        com.myra.assistant.model.ChatMessage(CommandParser.CREATOR_ANSWER, isUser = false)
                    )
                    inputBuffer = StringBuilder()
                    return@runOnUiThread
                }
                if (!isInCallMode) {
                    tryExecuteCommands(partial, tagsOnly = false, fromUser = true)
                }
            }
        }

        geminiLive.onOutputTranscript = { text ->
            runOnUiThread {
                outputBuffer.append(text)
                tryExecuteCommands(outputBuffer.toString(), tagsOnly = true)
            }
        }

        geminiLive.onTurnComplete = {
            runOnUiThread { flushTranscripts() }
        }

        geminiLive.onThinking = { thinking ->
            runOnUiThread {
                orbView.setState(if (thinking) OrbState.THINKING else OrbState.LISTENING)
            }
        }

        geminiLive.onError = { msg ->
            runOnUiThread {
                statusText.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        geminiLive.onDisconnected = {
            runOnUiThread {
                statusText.text = "Reconnecting..."
                orbView.setState(OrbState.IDLE)
            }
        }

        audioEngine.onMicChunk = { chunk ->
            if (::geminiLive.isInitialized) geminiLive.sendAudioPcm(chunk)
        }

        audioEngine.onAmplitudeChanged = { rms ->
            runOnUiThread {
                waveformView.setAmplitude(rms)
                orbView.setAmplitude(rms)
            }
        }

        audioEngine.onSpeakingStarted = {
            runOnUiThread {
                geminiLive.suppressMicSend = true
                orbView.setState(OrbState.SPEAKING)
                statusText.text = "Bol rahi hoon..."
            }
        }

        audioEngine.onSpeakingStopped = {
            runOnUiThread {
                geminiLive.suppressMicSend = false
                orbView.setState(OrbState.LISTENING)
                statusText.text = if (isInCallMode) {
                    getString(R.string.incoming_call_status_listening)
                } else {
                    "Sun rahi hoon..."
                }
            }
        }

        viewModel.commandResult.observe(this) { result ->
            result ?: return@observe
            if (::geminiLive.isInitialized) geminiLive.sendText(result)
            viewModel.clearCommandResult()
        }

        viewModel.actionFeedback.observe(this) { msg ->
            msg ?: return@observe
            statusText.text = msg
            viewModel.clearActionFeedback()
        }

        viewModel.whatsAppDraft.observe(this) { draft ->
            draft ?: return@observe
            AlertDialog.Builder(this)
                .setTitle("WhatsApp confirmation")
                .setMessage("To: ${draft.contactName}\n\nMessage:\n${draft.message}\n\nSend kar dun?")
                .setPositiveButton("Send") { _, _ ->
                    val res = viewModel.confirmWhatsAppSend()
                    statusText.text = res
                    if (::geminiLive.isInitialized) geminiLive.sendText(res)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    val res = viewModel.cancelWhatsAppSend()
                    statusText.text = res
                    if (::geminiLive.isInitialized) geminiLive.sendText(res)
                }
                .show()
        }

        geminiLive.connect()
    }

    private fun tryExecuteCommands(
        text: String,
        tagsOnly: Boolean = false,
        fromUser: Boolean = false
    ) {
        if (text.isBlank()) return
        if (isInCallMode) return
        if (fromUser && tryHandleWhatsAppInboxVoice(text)) return
        val primes = PrefsHelper.getPrimeContacts(this).map { it.name }
        val commands = CommandParser.parseCmdTags(text) +
            if (tagsOnly) emptyList() else CommandParser.parseAll(text, primes)
        val actionTypes = setOf(
            "OPEN_APP", "CLOSE_APP", "BACK", "HOME", "WHATSAPP_MSG", "WHATSAPP_NUMBER_MSG",
            "YOUTUBE_SEARCH", "GOOGLE_SEARCH", "CHROME_SEARCH", "MAPS_SEARCH",
            "CALL", "SMS", "VOLUME_UP", "VOLUME_DOWN", "FLASHLIGHT_ON", "FLASHLIGHT_OFF",
            "ADMIN_PIN", "UNLOCK",
            "DELETE_PHOTO", "WHATSAPP_PROFILE", "SOCIAL_POST", "PIN_LATEST_PHOTO",
            "SCROLL_DOWN", "SCROLL_UP"
        )
        for (cmd in commands.distinctBy { it.type + it.params.toString() }) {
            if (fromUser && cmd.type !in actionTypes) continue
            val key = cmd.type + cmd.params
            if (key == lastExecutedCommandKey) continue
            lastExecutedCommandKey = key
            viewModel.executeCommand(cmd)
            historyDb.insertCommand(CommandHistoryFormatter.format(cmd))
            if (fromUser && cmd.type in actionTypes) {
                statusText.text = "Kar diya ✓"
            }
        }
    }

    private fun tryHandleWhatsAppInboxVoice(text: String): Boolean {
        val pending = PendingWhatsAppInbox.current ?: return false
        val speaker = waInboxSpeaker ?: CallSpeaker(this).also {
            waInboxSpeaker = it
            it.initialize()
        }
        when (val action = WhatsAppInboxCommandParser.parse(text)) {
            WhatsAppInboxCommandParser.Action.DeclineRead -> {
                PendingWhatsAppInbox.clear()
                statusText.text = getString(R.string.wa_prime_declined)
                chatAdapter.addMessage(ChatMessage(text, isUser = true))
                return true
            }
            WhatsAppInboxCommandParser.Action.ReadAloud -> {
                val line = getString(R.string.wa_prime_reading, pending.prime.name, pending.body)
                speaker.speak(line) {
                    runOnUiThread { statusText.text = "Message sun liya ✓" }
                }
                chatAdapter.addMessage(ChatMessage(text, isUser = true))
                return true
            }
            is WhatsAppInboxCommandParser.Action.ReadAndReply -> {
                val line = getString(R.string.wa_prime_reading, pending.prime.name, pending.body)
                speaker.speak(line) {
                    val result = WhatsAppPrimeReplyHelper.sendReply(this, pending.prime, action.replyText)
                    runOnUiThread { statusText.text = result }
                }
                chatAdapter.addMessage(ChatMessage(text, isUser = true))
                PendingWhatsAppInbox.clear()
                return true
            }
            is WhatsAppInboxCommandParser.Action.Reply -> {
                val result = WhatsAppPrimeReplyHelper.sendReply(this, pending.prime, action.text)
                statusText.text = result
                chatAdapter.addMessage(ChatMessage(text, isUser = true))
                PendingWhatsAppInbox.clear()
                return true
            }
            WhatsAppInboxCommandParser.Action.Dismiss -> {
                PendingWhatsAppInbox.clear()
                statusText.text = "Theek hai"
                return true
            }
            null -> return false
        }
    }

    private fun flushTranscripts() {
        val userText = inputBuffer.toString().trim()
        val myraText = outputBuffer.toString().trim()
        inputBuffer = StringBuilder()
        outputBuffer = StringBuilder()
        lastExecutedCommandKey = ""

        if (userText.isNotEmpty()) {
            if (CommandParser.isCreatorQuestion(userText)) {
                geminiLive.sendText(CommandParser.CREATOR_ANSWER)
                chatAdapter.addMessage(ChatMessage(userText, isUser = true))
                chatAdapter.addMessage(ChatMessage(CommandParser.CREATOR_ANSWER, isUser = false))
            }
            val displayUser = userText.replace(Regex("""\[\[CMD:[^]]+]]"""), "").trim()
            if (displayUser.isNotEmpty()) {
                chatAdapter.addMessage(ChatMessage(displayUser, isUser = true))
            }
            if (isInCallMode) {
                tryHandleCallDecision(userText)
            } else {
                tryExecuteCommands(userText, tagsOnly = false, fromUser = true)
            }
        }
        if (myraText.isNotEmpty()) {
            val displayMyra = myraText.replace(Regex("""\[\[CMD:[^]]+]]"""), "").trim()
            if (displayMyra.isNotEmpty()) {
                chatAdapter.addMessage(ChatMessage(displayMyra, isUser = false))
            }
            if (!isInCallMode) tryExecuteCommands(myraText, tagsOnly = false)
        }
        findViewById<RecyclerView>(R.id.chatRecycler).scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun sendGreeting() {
        geminiLive.sendText(PrefsHelper.greetingText(this))
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioEngine.setMuted(isMuted)
        micButton.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        statusText.text = if (isMuted) "Muted" else "Sun rahi hoon..."
    }

    private fun maybeHandleIncomingFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CallMonitorService.EXTRA_INCOMING, false) != true) return
        val caller = CallerInfo(
            displayName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME).orEmpty(),
            phoneNumber = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NUMBER).orEmpty(),
            isSavedContact = intent.getBooleanExtra(CallMonitorService.EXTRA_IS_CONTACT, false)
        )
        handleIncomingPhoneCall(caller)
    }

    private fun handleIncomingPhoneCall(caller: CallerInfo) {
        if (isInCallMode && PendingIncomingCall.current?.phoneNumber == caller.phoneNumber) return
        PendingIncomingCall.set(caller)
        isInCallMode = true
        setActiveMode(true)
        homeContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        bottomNavigation.selectedItemId = R.id.nav_home
        if (!::geminiLive.isInitialized || !geminiInitialized) {
            tryStartMyra()
        }
        handler.postDelayed({ announceIncomingCallToAgent() }, 600)
    }

    private fun announceIncomingCallToAgent() {
        if (!isInCallMode) return
        val caller = PendingIncomingCall.current ?: return
        if (!::geminiLive.isInitialized) {
            handler.postDelayed({ announceIncomingCallToAgent() }, 800)
            return
        }
        geminiLive.suppressMicSend = false
        val who = when {
            caller.isSavedContact -> caller.displayName
            caller.phoneNumber.isNotBlank() -> caller.phoneNumber
            else -> getString(R.string.unknown_caller)
        }
        val instruction = if (PrefsHelper.shouldSpeakCallerName(this)) {
            getString(R.string.incoming_call_agent_prompt_named, who)
        } else {
            getString(R.string.incoming_call_agent_prompt_unknown)
        }
        geminiLive.sendText(instruction)
        orbView.setState(OrbState.LISTENING)
        statusText.text = getString(R.string.incoming_call_status_listening)
        if (::audioEngine.isInitialized) {
            audioEngine.setMuted(false)
            isMuted = false
        }
    }

    private fun tryHandleCallDecision(text: String): Boolean {
        if (!isInCallMode) return false
        val decision = CommandParser.parseCallDecision(text) ?: return false
        applyCallDecision(decision, text)
        return true
    }

    private fun applyCallDecision(decision: String, userText: String = "") {
        val ok = when (decision) {
            "ACCEPT" -> CallActionHelper.accept(this)
            "REJECT" -> CallActionHelper.reject(this)
            else -> return
        }
        val msg = when (decision) {
            "ACCEPT" -> if (ok) getString(R.string.call_accepted) else getString(R.string.call_accept_failed)
            else -> if (ok) getString(R.string.call_rejected) else getString(R.string.call_reject_failed)
        }
        if (userText.isNotBlank()) {
            chatAdapter.addMessage(ChatMessage(userText, isUser = true))
        }
        chatAdapter.addMessage(ChatMessage(msg, isUser = false))
        if (::geminiLive.isInitialized) geminiLive.sendText(msg)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        sendBroadcast(Intent(ACTION_CALL_HANDLED).setPackage(packageName))
        finishIncomingCallMode()
    }

    private fun finishIncomingCallMode() {
        isInCallMode = false
        PendingIncomingCall.clear()
        if (::geminiLive.isInitialized) geminiLive.suppressMicSend = false
    }

    private fun setActiveMode(active: Boolean) {
        isActiveMode = active
        val targetAlpha = if (active) 0.08f else 0f
        ObjectAnimator.ofFloat(redOverlay, View.ALPHA, redOverlay.alpha, targetAlpha).apply {
            duration = if (active) 300 else 500
            start()
        }
    }

    private fun checkPermissions() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && hasMicPermission()) {
            lifecycleScope.launch(Dispatchers.IO) { AppLauncher.warmCache(this@MainActivity) }
            tryStartMyra()
        } else if (requestCode == 100) {
            statusText.text = "Mic permission denied — enable in Settings"
            Toast.makeText(this, "MYRA needs microphone permission", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSystemServices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, CallMonitorService::class.java))
            startForegroundService(Intent(this, MyraOverlayService::class.java))
        } else {
            startService(Intent(this, CallMonitorService::class.java))
            startService(Intent(this, MyraOverlayService::class.java))
        }
    }

    private fun startStatusUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                updateStatusBar()
                handler.postDelayed(this, 30_000)
            }
        }
        handler.post(runnable)
        updateStatusBar()
    }

    private fun updateStatusBar() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        batteryText.text = "🔋 $level%"

        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        ramText.text = "RAM ${usedMb}MB"

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeText.text = fmt.format(Date())
    }

    override fun onResume() {
        super.onResume()
        PersonalizationStore.recordSession(this)
        if (::audioEngine.isInitialized) audioEngine.setMuted(isMuted)
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            OverlayPermissionHelper.requestIfNeeded(this)
        }
        if (hasMicPermission() && PrefsHelper.getApiKey(this).isNotBlank()) {
            lifecycleScope.launch(Dispatchers.IO) { AppLauncher.warmCache(this@MainActivity) }
            tryStartMyra()
        }
        NotificationListenerHelper.requestRebind(this)
    }

    override fun onDestroy() {
        if (::geminiLive.isInitialized) geminiLive.destroy()
        if (::audioEngine.isInitialized) audioEngine.release()
        geminiInitialized = false
        try { unregisterReceiver(callEndedReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(incomingCallReceiver) } catch (_: Exception) {}
        waInboxSpeaker?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CALL_USER_DECISION = "com.myra.CALL_USER_DECISION"
        const val ACTION_CALL_HANDLED = "com.myra.CALL_HANDLED"
        const val EXTRA_DECISION = "decision"

        fun incomingCallIntent(context: Context, caller: CallerInfo): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(CallMonitorService.EXTRA_INCOMING, true)
                putExtra(CallMonitorService.EXTRA_CALLER_NAME, caller.displayName)
                putExtra(CallMonitorService.EXTRA_CALLER_NUMBER, caller.phoneNumber)
                putExtra(CallMonitorService.EXTRA_IS_CONTACT, caller.isSavedContact)
            }
        }

        fun notifyCallDecision(context: Context, decision: String) {
            context.sendBroadcast(
                Intent(ACTION_CALL_USER_DECISION).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_DECISION, decision)
                }
            )
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
