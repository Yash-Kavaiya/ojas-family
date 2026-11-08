package com.ojashealing.family.data.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager

/** Where the other person's voice comes out. */
enum class AudioRoute { Earpiece, Speaker, Bluetooth, Wired }

/**
 * The call's audio output: the phone's earpiece, the loudspeaker, a Bluetooth headset or wired
 * headphones. Android 12+ chooses devices directly; older phones switch speakerphone and Bluetooth SCO.
 * Also turns the screen off near the ear on the earpiece, so a cheek cannot press buttons.
 */
class CallAudio(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximity: PowerManager.WakeLock? = null

    /** What this phone can use right now; Earpiece and Speaker always. */
    fun available(): List<AudioRoute> {
        val found = mutableSetOf(AudioRoute.Earpiece, AudioRoute.Speaker)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.availableCommunicationDevices.forEach { d -> routeOf(d.type)?.let(found::add) }
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { d -> routeOf(d.type)?.let(found::add) }
        }
        return AudioRoute.entries.filter { it in found }
    }

    private fun routeOf(type: Int): AudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.Wired
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) AudioRoute.Bluetooth else null
    }

    /** Headphones or a headset win over the earpiece when the call starts, like a phone call. */
    fun preferred(video: Boolean): AudioRoute {
        val a = available()
        return when {
            AudioRoute.Bluetooth in a -> AudioRoute.Bluetooth
            AudioRoute.Wired in a -> AudioRoute.Wired
            video -> AudioRoute.Speaker
            else -> AudioRoute.Earpiece
        }
    }

    @Suppress("DEPRECATION")
    fun select(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audio.availableCommunicationDevices.firstOrNull { routeOf(it.type) == route }
            if (device != null) audio.setCommunicationDevice(device) else audio.clearCommunicationDevice()
        } else {
            audio.isSpeakerphoneOn = route == AudioRoute.Speaker
            if (route == AudioRoute.Bluetooth) { audio.startBluetoothSco(); audio.isBluetoothScoOn = true }
            else { audio.isBluetoothScoOn = false; audio.stopBluetoothSco() }
        }
    }

    /** Screen off near the ear: on for an audio call through the earpiece, off otherwise. */
    fun nearEar(on: Boolean) {
        if (on) {
            if (proximity?.isHeld == true) return
            if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximity = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ojas:call-proximity").apply { acquire(4 * 60 * 60 * 1000L) }
        } else {
            runCatching { if (proximity?.isHeld == true) proximity?.release() }
            proximity = null
        }
    }

    @Suppress("DEPRECATION")
    fun release() {
        nearEar(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
        else { audio.isBluetoothScoOn = false; audio.stopBluetoothSco() }
    }
}
