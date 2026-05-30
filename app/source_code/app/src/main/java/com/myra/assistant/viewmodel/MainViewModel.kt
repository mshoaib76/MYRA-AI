package com.myra.assistant.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.myra.assistant.util.CallActionHelper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.admin.AdminManager
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.agent.HumanAgent
import com.myra.assistant.util.AppActions
import com.myra.assistant.util.AppLauncher
import com.myra.assistant.util.ContactResolver
import com.myra.assistant.util.PrefsHelper
import com.myra.assistant.service.PendingActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    private val _actionFeedback = MutableLiveData<String?>()
    val actionFeedback: LiveData<String?> = _actionFeedback

    data class WhatsAppDraft(val contactName: String, val message: String)

    private val _whatsAppDraft = MutableLiveData<WhatsAppDraft?>()
    val whatsAppDraft: LiveData<WhatsAppDraft?> = _whatsAppDraft

    private var pendingWhatsAppSend: WhatsAppDraft? = null

    private val actionOnlyTypes = setOf(
        "OPEN_APP", "CLOSE_APP", "BACK", "HOME", "WHATSAPP_MSG", "WHATSAPP_NUMBER_MSG", "WHATSAPP_SEND",
        "YOUTUBE_SEARCH", "GOOGLE_SEARCH", "CHROME_SEARCH", "MAPS_SEARCH",
        "CALL", "SMS", "VOLUME_UP", "VOLUME_DOWN", "FLASHLIGHT_ON", "FLASHLIGHT_OFF",
        "DELETE_PHOTO", "WHATSAPP_PROFILE", "SOCIAL_POST", "PIN_LATEST_PHOTO",
        "SCROLL_DOWN", "SCROLL_UP"
    )

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCommand(command) }
            _actionFeedback.postValue(result)
            if (command.type !in actionOnlyTypes) {
                _commandResult.postValue(result)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), result, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun runCommand(command: AppCommand): String {
        val ctx = getApplication<Application>()
        return try {
            when (command.type) {
                "OPEN_APP" -> openApp(command.params["app_name"] ?: "")
                "CLOSE_APP" -> closeApp()
                "CALL" -> makeCall(command.params["name"] ?: "")
                "SMS" -> sendSms(command.params["name"] ?: "", command.params["message"] ?: "")
                "WHATSAPP_MSG" -> whatsAppMsg(command.params["name"] ?: "", command.params["message"] ?: "")
                "WHATSAPP_SEND" -> confirmWhatsAppSend()
                "WHATSAPP_CALL" -> whatsAppCall(command.params["name"] ?: "")
                "PRIME_CALL" -> primeCall(command.params["index"]?.toIntOrNull() ?: 0)
                "PRIME_MSG" -> primeMsg(command.params["index"]?.toIntOrNull() ?: 0)
                "VOLUME_UP" -> adjustVolume(true)
                "VOLUME_DOWN" -> adjustVolume(false)
                "FLASHLIGHT_ON" -> setFlashlight(true)
                "FLASHLIGHT_OFF" -> setFlashlight(false)
                "WIFI_ON" -> setWifi(true)
                "WIFI_OFF" -> setWifi(false)
                "BLUETOOTH_ON" -> setBluetooth(true)
                "BLUETOOTH_OFF" -> setBluetooth(false)
                "SCROLL_DOWN" -> scrollScreen(true)
                "SCROLL_UP" -> scrollScreen(false)
                "HOME" -> pressHome()
                "BACK" -> pressBack()
                "WHATSAPP_NUMBER_MSG" -> whatsAppNumberMsg(
                    command.params["number"] ?: "",
                    command.params["message"] ?: ""
                )
                "ADMIN_PIN" -> verifyAdminPin(command.params["pin"] ?: "")
                "ADMIN_PATTERN" -> verifyAdminPattern(command.params["pattern"] ?: "")
                "UNLOCK" -> unlockWithVault(command.params["target"] ?: "")
                "YOUTUBE_SEARCH" -> AppActions.youtubeSearch(ctx, command.params["query"] ?: "")
                "GOOGLE_SEARCH" -> AppActions.googleSearch(ctx, command.params["query"] ?: "")
                "CHROME_SEARCH" -> AppActions.chromeSearch(ctx, command.params["query"] ?: "")
                "MAPS_SEARCH" -> AppActions.mapsSearch(ctx, command.params["query"] ?: "")
                "DELETE_PHOTO" -> HumanAgent.deletePhoto(ctx, command.params["hint"] ?: "latest")
                "WHATSAPP_PROFILE" -> HumanAgent.setWhatsAppProfilePhoto(
                    ctx, command.params["image"] ?: "latest"
                )
                "SOCIAL_POST" -> HumanAgent.postOnSocial(
                    ctx,
                    command.params["platform"] ?: "facebook",
                    command.params["caption"] ?: "",
                    command.params["image"] ?: "latest"
                )
                "PIN_LATEST_PHOTO" -> HumanAgent.pinLatestPhoto(ctx)
                else -> "Unknown command"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun openApp(appName: String): String {
        val ctx = getApplication<Application>()
        val result = AppLauncher.open(ctx, appName)
        maybeAutoUnlock(ctx, appName)
        return result
    }

    private fun maybeAutoUnlock(ctx: Context, appName: String) {
        val normalized = AppLauncher.normalizeQuery(appName)
        if (!normalized.contains("whatsapp") && normalized != "wa" && normalized != "watsapp") return
        if (!AccessibilityHelperService.isEnabled(ctx)) return
        val entry = AdminManager.findVaultSecret(ctx, "whatsapp") ?: return
        val svc = AccessibilityHelperService.instance ?: return
        viewModelScope.launch {
            // App lock UI usually appears after app launch; wait briefly then enter saved secret.
            kotlinx.coroutines.delay(1200)
            val ok = withContext(Dispatchers.IO) { svc.enterSecret(entry.secret, entry.usePattern) }
            _actionFeedback.postValue(
                if (ok) "WhatsApp unlock try kiya ✓"
                else "WhatsApp unlock nahi hua — lock screen par raho aur dubara bolo"
            )
        }
    }

    private fun pressHome(): String {
        val ctx = getApplication<Application>()
        return if (AccessibilityHelperService.isEnabled(ctx)) {
            AccessibilityHelperService.instance?.closeCurrentApp()
            "Home screen"
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            "Home screen"
        }
    }

    private fun pressBack(): String {
        val ctx = getApplication<Application>()
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            return "Enable MYRA Accessibility for back button"
        }
        return if (AccessibilityHelperService.instance?.goBack() == true) "Back" else "Back failed"
    }

    private fun scrollScreen(down: Boolean): String {
        val ctx = getApplication<Application>()
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            return "Scroll ke liye MYRA Accessibility ON karo"
        }
        val svc = AccessibilityHelperService.instance ?: return "Accessibility service connect nahi"
        val ok = if (down) svc.scrollDown() else svc.scrollUp()
        return when {
            ok && down -> "Scroll down ✓"
            ok -> "Scroll up ✓"
            down -> "Scroll down nahi hua"
            else -> "Scroll up nahi hua"
        }
    }

    private fun setFlashlight(on: Boolean): String {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "Torch not supported"
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull() ?: return "No camera"
            cm.setTorchMode(id, on)
            if (on) "Torch on ✓" else "Torch off ✓"
        } catch (e: Exception) {
            "Torch error: ${e.message}"
        }
    }

    private fun closeApp(): String {
        val ctx = getApplication<Application>()
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            AccessibilityHelperService.openSettings(ctx)
            return "Accessibility ON karo — phir app band hogi"
        }
        val svc = AccessibilityHelperService.instance
        if (svc?.closeCurrentApp() == true) return "App band kar di ✓"
        return "App band nahi hui — dubara bolo"
    }

    private fun verifyAdminPin(pin: String): String {
        val ctx = getApplication<Application>()
        if (!AdminManager.isAdminEnabled(ctx)) return "Admin mode Settings se set karo"
        return if (AdminManager.verifyPin(ctx, pin)) {
            "Admin mode ON ✓ — ab unlock commands use kar sakte ho"
        } else {
            "Galat PIN"
        }
    }

    private fun verifyAdminPattern(pattern: String): String {
        val ctx = getApplication<Application>()
        if (!AdminManager.isAdminEnabled(ctx)) return "Admin pattern Settings se set karo"
        return if (AdminManager.verifyPattern(ctx, pattern)) {
            "Pattern sahi — admin session active ✓"
        } else {
            "Galat pattern"
        }
    }

    private fun unlockWithVault(target: String): String {
        val ctx = getApplication<Application>()
        if (AdminManager.isAdminEnabled(ctx) && !AdminManager.isSessionActive()) {
            return "Pehle admin PIN bolo: admin 1234 (apna PIN)"
        }
        val entry = AdminManager.findVaultSecret(ctx, AppLauncher.normalizeQuery(target))
            ?: AdminManager.findVaultSecret(ctx, target)
            ?: return "Vault mein '$target' nahi mila — Settings → Admin Vault mein add karo"
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            AccessibilityHelperService.openSettings(ctx)
            return "Accessibility ON karo unlock ke liye"
        }
        val svc = AccessibilityHelperService.instance ?: return "Accessibility service connect nahi"
        val ok = svc.enterSecret(entry.secret, entry.usePattern)
        AdminManager.extendSession()
        return if (ok) "Unlock try kiya: ${entry.label} ✓" else "Unlock fail — screen par lock kholo, phir dubara bolo"
    }

    private fun whatsAppNumberMsg(number: String, message: String): String {
        val ctx = getApplication<Application>()
        val phone = ContactResolver.forWhatsApp(ContactResolver.normalizeDigits(number))
        if (phone.length < 8) return "Sahi number bolo"
        return AppActions.openWhatsAppChat(ctx, phone, message.trim(), autoSend = message.isNotBlank())
    }

    private fun makeCall(nameOrNumber: String): String {
        val number = resolvePhoneNumber(nameOrNumber) ?: nameOrNumber.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return "Contact not found: $nameOrNumber"
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
        return "Calling $nameOrNumber"
    }

    private fun sendSms(name: String, message: String): String {
        val number = resolvePhoneNumber(name) ?: return "Contact not found: $name"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
        return "Opening SMS to $name"
    }

    private fun whatsAppMsg(name: String, message: String): String {
        val ctx = getApplication<Application>()
        if (message.isBlank()) return "Message kya bhejna hai? dubara bolo"
        val number = ContactResolver.findPhone(ctx, name)
            ?: return "Contact '$name' nahi mila — Contacts permission ON karo"
        val phone = ContactResolver.forWhatsApp(number)
        val msg = message.trim()
        val result = AppActions.openWhatsAppChat(ctx, phone, msg, autoSend = false)
        val draft = WhatsAppDraft(contactName = name, message = msg)
        pendingWhatsAppSend = draft
        _whatsAppDraft.postValue(draft)
        return "$result. Send karun?"
    }

    fun confirmWhatsAppSend(): String {
        val ctx = getApplication<Application>()
        val draft = pendingWhatsAppSend ?: return "Koi pending WhatsApp message nahi"
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            AccessibilityHelperService.openSettings(ctx)
            return "Send ke liye MYRA Accessibility ON karo"
        }
        pendingWhatsAppSend = null
        _whatsAppDraft.postValue(null)
        PendingActionRunner.scheduleWhatsAppSend(attempts = 6)
        return "WhatsApp send kar diya ✓ (${draft.contactName})"
    }

    fun cancelWhatsAppSend(): String {
        val draft = pendingWhatsAppSend ?: return "Koi pending WhatsApp message nahi"
        pendingWhatsAppSend = null
        _whatsAppDraft.postValue(null)
        return "Ok, send cancel ✓ (${draft.contactName})"
    }

    private fun whatsAppCall(name: String): String {
        val number = resolvePhoneNumber(name)?.filter { it.isDigit() } ?: return "Contact not found"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
        return "Opening WhatsApp call for $name"
    }

    private fun primeCall(index: Int): String {
        val contacts = PrefsHelper.getPrimeContacts(getApplication())
        val contact = contacts.getOrNull(index) ?: return "Prime contact not found at index $index"
        return makeCall(contact.number).let { "Calling ${contact.name}" }
    }

    private fun primeMsg(index: Int): String {
        val contacts = PrefsHelper.getPrimeContacts(getApplication())
        val contact = contacts.getOrNull(index) ?: return "Prime contact not found"
        return sendSms(contact.name, "")
    }

    private fun resolvePhoneNumber(nameOrNumber: String): String? =
        ContactResolver.findPhone(getApplication(), nameOrNumber)

    fun acceptCall(): String {
        val ctx = getApplication<Application>()
        return if (CallActionHelper.accept(ctx)) "Call accepted" else "Could not accept call"
    }

    fun rejectCall(): String {
        val ctx = getApplication<Application>()
        return if (CallActionHelper.reject(ctx)) "Call rejected" else "Could not reject call"
    }

    private fun adjustVolume(up: Boolean): String {
        val am = getApplication<Application>().getSystemService(AudioManager::class.java)
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        @Suppress("DEPRECATION")
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return if (up) "Volume increased" else "Volume decreased"
    }

    @Suppress("DEPRECATION")
    private fun setWifi(on: Boolean): String {
        val wm = getApplication<Application>().applicationContext.getSystemService(WifiManager::class.java)
        wm.isWifiEnabled = on
        return if (on) "WiFi turned on" else "WiFi turned off"
    }

    private fun setBluetooth(on: Boolean): String {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return "Bluetooth not available"
        if (on) adapter.enable() else adapter.disable()
        return if (on) "Bluetooth on" else "Bluetooth off"
    }

    fun clearCommandResult() {
        _commandResult.value = null
    }

    fun clearActionFeedback() {
        _actionFeedback.value = null
    }
}
