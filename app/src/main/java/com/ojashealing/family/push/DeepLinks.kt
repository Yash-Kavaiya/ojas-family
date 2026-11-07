package com.ojashealing.family.push

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a tapped notification asked to open ("questions/x", "chats/uid", "calls/id", ...), until the app shows it. */
object DeepLinks {
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target.asStateFlow()

    fun from(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TARGET)?.takeIf { it.isNotBlank() }?.let { _target.value = it }
        intent?.removeExtra(EXTRA_TARGET)
    }

    fun consume() {
        _target.value = null
    }
}
