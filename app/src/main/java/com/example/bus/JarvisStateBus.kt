package com.example.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class JarvisSystemEvent {
    data class NotificationReceived(
        val appPackage: String,
        val sender: String,
        val messageText: String,
        val canReply: Boolean
    ) : JarvisSystemEvent()

    data class VoiceTranscription(val text: String, val isFinal: Boolean) : JarvisSystemEvent()
    data class HotwordDetected(val phrase: String) : JarvisSystemEvent()
    data class SystemActionResult(val success: Boolean, val badge: String, val message: String) : JarvisSystemEvent()
    data class PhotoCaptured(val uriString: String) : JarvisSystemEvent()
    data class TextRead(val text: String) : JarvisSystemEvent()
    data class SentinelAlert(val latitude: Double, val longitude: Double, val status: String) : JarvisSystemEvent()
}

object JarvisStateBus {
    private val _events = MutableSharedFlow<JarvisSystemEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<JarvisSystemEvent> = _events.asSharedFlow()

    private val _isDrivingMode = MutableStateFlow(false)
    val isDrivingMode: StateFlow<Boolean> = _isDrivingMode.asStateFlow()

    private val _isMicrophoneInUse = MutableStateFlow(false)
    val isMicrophoneInUse: StateFlow<Boolean> = _isMicrophoneInUse.asStateFlow()

    fun postEvent(event: JarvisSystemEvent) {
        _events.tryEmit(event)
    }

    fun setDrivingMode(enabled: Boolean) {
        _isDrivingMode.value = enabled
    }

    fun setMicrophoneInUse(inUse: Boolean) {
        _isMicrophoneInUse.value = inUse
    }
}
