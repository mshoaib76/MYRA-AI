package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun closeCurrentApp(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun scrollDown(): Boolean {
        val node = findScrollable(rootInActiveWindow) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp(): Boolean {
        val node = findScrollable(rootInActiveWindow) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun clickOnText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (clickNodeOrParent(node)) return true
        }
        return false
    }

    fun clickByViewId(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (clickNodeOrParent(node)) return true
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        return false
    }

    /** Tap WhatsApp send button (multiple package / language variants). */
    /** Tap answer on system or OEM incoming-call screen. */
    fun answerIncomingCall(): Boolean {
        val labels = listOf(
            "Answer", "ANSWER", "Accept", "Pick up", "Pick-up",
            "اٹھائیں", "जवाब", "Atender", "Répondre"
        )
        for (label in labels) {
            if (clickOnText(label)) return true
        }
        val root = rootInActiveWindow ?: return false
        return findClickableByContentDescription(root, "answer") ||
            findClickableByContentDescription(root, "accept")
    }

    /** Tap decline / reject on incoming-call UI. */
    fun declineIncomingCall(): Boolean {
        val labels = listOf(
            "Decline", "Reject", "Dismiss", "Ignore", "Cut",
            "رد کریں", "انکار", "अस्वीकार", "Rechazar", "Refuser"
        )
        for (label in labels) {
            if (clickOnText(label)) return true
        }
        val root = rootInActiveWindow ?: return false
        return findClickableByContentDescription(root, "decline") ||
            findClickableByContentDescription(root, "reject")
    }

    fun tapWhatsAppSend(): Boolean {
        val sendIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/conversation_entry_action_button"
        )
        for (id in sendIds) {
            if (clickByViewId(id)) return true
        }
        val labels = listOf("Send", "SEND", "بھیجیں", "भेजें", "Отправить")
        for (label in labels) {
            if (clickOnText(label)) return true
        }
        val root = rootInActiveWindow ?: return false
        return findClickableByContentDescription(root, "send")
    }

    private fun findClickableByContentDescription(
        node: AccessibilityNodeInfo,
        keyword: String
    ): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains(keyword) && clickNodeOrParent(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findClickableByContentDescription(child, keyword)) return true
        }
        return false
    }

    fun focusSearchAndType(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = findSearchField(root) ?: findEditText(root) ?: return false
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        Thread.sleep(400)
        return clickOnText("Search") || clickOnText("खोजें")
    }

    private fun findSearchField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText") &&
            (hint.contains("search") || desc.contains("search") || hint.contains("खोज") || desc.contains("تلاش"))
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            findSearchField(node.getChild(i))?.let { return it }
        }
        return null
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = findEditText(root) ?: return false
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Types PIN/password on lock or app-lock screen (user must open target app first). */
    fun enterSecret(secret: String, asPattern: Boolean): Boolean {
        if (secret.isBlank()) return false
        if (asPattern) {
            return typePattern(secret)
        }
        return typeText(secret) || typeDigitByDigit(secret)
    }

    private fun typeDigitByDigit(secret: String): Boolean {
        var ok = false
        for (ch in secret) {
            if (!ch.isDigit()) continue
            val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(ch.toString())
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        ok = true
                        break
                    }
                    var p = node.parent
                    while (p != null) {
                        if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            ok = true
                            break
                        }
                        p = p.parent
                    }
                }
            }
            Thread.sleep(120)
        }
        return ok
    }

    private fun typePattern(pattern: String): Boolean {
        val digits = pattern.filter { it.isDigit() }.map { it.toString().toInt() }.filter { it in 1..9 }
        if (digits.size < 2) return false

        val root = rootInActiveWindow ?: return false
        var patternNode: AccessibilityNodeInfo? = null

        fun findPatternView(node: AccessibilityNodeInfo) {
            val cls = node.className?.toString() ?: ""
            if (cls.contains("PatternView", ignoreCase = true) || cls.contains("LockPatternView", ignoreCase = true)) {
                patternNode = node
                return
            }
            for (i in 0 until node.childCount) {
                if (patternNode != null) return
                node.getChild(i)?.let { findPatternView(it) }
            }
        }
        findPatternView(root)

        val rect = android.graphics.Rect()
        if (patternNode != null) {
            patternNode!!.getBoundsInScreen(rect)
        } else {
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val size = (w * 0.8).toInt()
            rect.set((w - size)/2, h - size - (h*0.1).toInt(), (w + size)/2, h - (h*0.1).toInt())
        }

        val stepX = rect.width() / 3f
        val stepY = rect.height() / 3f
        val startX = rect.left + stepX / 2f
        val startY = rect.top + stepY / 2f

        val path = android.graphics.Path()
        var first = true
        for (d in digits) {
            val col = (d - 1) % 3
            val row = (d - 1) / 3
            val x = startX + col * stepX
            val y = startY + row * stepY
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 500L * digits.size)
            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(stroke)
            return dispatchGesture(builder.build(), null, null)
        }
        return false
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    fun requireEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        Toast.makeText(context, "Enable MYRA Accessibility in Settings", Toast.LENGTH_LONG).show()
        openSettings(context)
        return false
    }
}
