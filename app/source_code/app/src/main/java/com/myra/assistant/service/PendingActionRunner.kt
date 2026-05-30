package com.myra.assistant.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.myra.assistant.service.UiAutomation.clickAny
import com.myra.assistant.service.UiAutomation.clickContains
import com.myra.assistant.service.UiAutomation.typeInComposer

/** Runs accessibility actions after apps open (multi-step human-like flows). */
object PendingActionRunner {

    private const val TAG = "PendingAction"
    private val handler = Handler(Looper.getMainLooper())

    fun scheduleWhatsAppSend(attempts: Int = 5, delayMs: Long = 1800L) {
        var left = attempts
        fun trySend() {
            val svc = AccessibilityHelperService.instance
            if (svc == null) {
                if (--left > 0) handler.postDelayed({ trySend() }, delayMs)
                return
            }
            val sent = svc.tapWhatsAppSend()
            Log.d(TAG, "WhatsApp send tap=$sent left=$left")
            if (!sent && --left > 0) handler.postDelayed({ trySend() }, delayMs)
        }
        handler.postDelayed({ trySend() }, delayMs)
    }

    fun scheduleWhatsAppProfilePhoto() {
        val steps: List<Pair<Long, () -> Boolean>> = listOf(
            3200L to {
                svc()?.clickAny(
                    listOf(
                        "More options", "Settings", "Setting", "Profile",
                        "پروفائل", "تنظیمات"
                    )
                ) == true
            },
            1800L to {
                svc()?.clickAny(
                    listOf(
                        "Profile photo", "Profile picture", "Edit",
                        "پروفائل فوٹو", "Camera", "Gallery"
                    )
                ) == true || svc()?.clickContains("profile") == true
            },
            1500L to {
                svc()?.clickAny(
                    listOf("Gallery", "Photos", "Choose from gallery", "گیلری", "Documents")
                ) == true
            },
            1200L to {
                svc()?.clickAny(listOf("Recent", "Download", "Camera", "Pictures")) == true
            },
            1200L to {
                svc()?.clickContains("Image") == true ||
                    svc()?.clickContains("Photo") == true
            },
            1000L to {
                svc()?.clickAny(listOf("OK", "Done", "Save", "SAVE", "Crop", "Apply", "Set")) == true
            }
        )
        runSteps(steps, "WA_PROFILE")
    }

    fun scheduleSocialPost(platform: String, caption: String) {
        val postLabels = when (platform.lowercase()) {
            "facebook", "fb" -> listOf("Post", "POST", "Share now", "Publish now", "Share", "پوسٹ")
            "instagram", "insta" -> listOf("Share", "Next", "Post", "OK", "Done")
            "tiktok" -> listOf("Post", "Publish", "Next", "Share")
            else -> listOf("Post", "Share", "Next", "Send", "Publish")
        }
        val steps: List<Pair<Long, () -> Boolean>> = listOf(
            2800L to {
                if (caption.isNotBlank()) svc()?.typeInComposer(caption) == true else true
            },
            1500L to {
                svc()?.clickAny(listOf("Next", "Continue", "OK")) == true
            },
            1200L to {
                svc()?.clickAny(postLabels, 12) == true
            }
        )
        runSteps(steps, "SOCIAL_$platform")
    }

    private fun svc() = AccessibilityHelperService.instance

    private fun runSteps(steps: List<Pair<Long, () -> Boolean>>, label: String) {
        fun runAt(index: Int, cumulativeDelay: Long) {
            if (index >= steps.size) {
                Log.d(TAG, "$label chain done")
                return
            }
            val (delay, action) = steps[index]
            val total = cumulativeDelay + delay
            handler.postDelayed({
                try {
                    val ok = action()
                    Log.d(TAG, "$label step $index ok=$ok")
                } catch (e: Exception) {
                    Log.e(TAG, "$label step $index", e)
                }
                runAt(index + 1, total)
            }, delay)
        }
        runAt(0, 0L)
    }
}
