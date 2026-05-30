package com.myra.assistant.service

import android.view.accessibility.AccessibilityNodeInfo

/** Extra UI helpers for multi-step human-like flows. */
object UiAutomation {

    fun AccessibilityHelperService.clickAny(labels: List<String>, maxAttempts: Int = 8): Boolean {
        repeat(maxAttempts) {
            for (label in labels) {
                if (clickOnText(label)) return true
                if (clickContains(label)) return true
            }
            Thread.sleep(450)
        }
        return false
    }

    fun AccessibilityHelperService.clickContains(partial: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickNodeTreeContains(root, partial.lowercase())
    }

    private fun AccessibilityHelperService.clickNodeTreeContains(
        node: AccessibilityNodeInfo,
        keyword: String
    ): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if ((text.contains(keyword) || desc.contains(keyword)) && node.isClickable) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        if (text.contains(keyword) || desc.contains(keyword)) {
            var p = node.parent
            while (p != null) {
                if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                p = p.parent
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNodeTreeContains(child, keyword)) return true
        }
        return false
    }

    fun AccessibilityHelperService.typeInComposer(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = findComposerField(root) ?: findEditText(root) ?: return false
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findComposerField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText") && (
                hint.contains("what") || hint.contains("mind") || hint.contains("caption") ||
                    hint.contains("say") || hint.contains("write") || hint.contains("describe") ||
                    hint.contains("kya") || hint.contains("soch")
                )
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            findComposerField(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun AccessibilityHelperService.findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i))?.let { return it }
        }
        return null
    }
}
