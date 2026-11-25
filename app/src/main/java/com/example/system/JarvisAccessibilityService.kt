package com.example.system

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
        var isServiceEnabled: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceEnabled = true
        Log.i("JarvisAccessibility", "Jarvis Accessibility Service connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used to track context or active app notifications if needed
    }

    override fun onInterrupt() {
        instance = null
        isServiceEnabled = false
        Log.i("JarvisAccessibility", "Jarvis Accessibility Service interrupted.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        isServiceEnabled = false
        Log.i("JarvisAccessibility", "Jarvis Accessibility Service unbound.")
        return super.onUnbind(intent)
    }

    /**
     * Reads all text currently visible on the active screen windows.
     */
    fun readCurrentScreenText(): String {
        val rootNode = rootInActiveWindow ?: return "No active window content available."
        val stringBuilder = StringBuilder()
        traverseNodeForText(rootNode, stringBuilder)
        rootNode.recycle()
        return stringBuilder.toString().trim()
    }

    private fun traverseNodeForText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            sb.append(text).append(" ")
        } else if (!contentDesc.isNullOrBlank()) {
            sb.append(contentDesc).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNodeForText(child, sb)
                child.recycle()
            }
        }
    }

    /**
     * Automatically types and sends a message in WhatsApp or Telegram if currently open on chat view.
     */
    fun autoSendSocialMessage(contactName: String, messageText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        var success = false

        // Try to find an EditText or message input field
        val inputNode = findNodeByCondition(rootNode) { node ->
            node.isEditable || node.className?.toString()?.contains("EditText") == true ||
            node.viewIdResourceName?.contains("entry") == true ||
            node.viewIdResourceName?.contains("message") == true
        }

        if (inputNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, messageText)
            }
            success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            inputNode.recycle()

            if (success) {
                // Now find the Send button
                val sendButton = findNodeByCondition(rootNode) { node ->
                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    val text = node.text?.toString()?.lowercase() ?: ""
                    val id = node.viewIdResourceName?.lowercase() ?: ""
                    desc.contains("send") || desc.contains("enviar") ||
                    text.contains("send") || text.contains("enviar") ||
                    id.contains("send") || id.contains("button_send")
                }
                if (sendButton != null) {
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    sendButton.recycle()
                }
            }
        }

        rootNode.recycle()
        return success
    }

    private fun findNodeByCondition(root: AccessibilityNodeInfo, condition: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (condition(root)) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                val found = findNodeByCondition(child, condition)
                child.recycle()
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Performs system-level actions.
     */
    fun executeSystemAction(action: String): Boolean {
        return when (action.lowercase()) {
            "back", "atras" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home", "inicio" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents", "recientes" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications", "notificaciones" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings", "ajustes_rapidos" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "lock", "bloquear" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "power", "apagado" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "screenshot", "captura" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            else -> false
        }
    }
}
