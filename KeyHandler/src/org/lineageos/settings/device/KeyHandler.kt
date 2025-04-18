/*
 * Copyright (C) 2021-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioSystem
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler
import java.io.File

class KeyHandler(private val context: Context) : DeviceKeyHandler {

    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val notificationManager = context.getSystemService(NotificationManager::class.java)!!
    private val vibrator: Vibrator?

    private val packageContext =
        context.createPackageContext(KeyHandler::class.java.getPackage()!!.name, 0)
    private val sharedPreferences
        get() =
            packageContext.getSharedPreferences(
                packageContext.packageName + "_preferences",
                Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
            )

    private val handlerThread = HandlerThread("KeyHandlerThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private var needsRun = false
    private var prevKeyCode = 0

    private var wasMuted = false
    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                val state = intent.getBooleanExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false)
                if (stream == AudioSystem.STREAM_MUSIC && !state) {
                    wasMuted = false
                }
            }
        }

    init {
        context.registerReceiver(
            broadcastReceiver,
            IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION)
        )
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager 
        vibrator = vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

        if (vibrator == null || !vibrator.hasVibrator()) {
            println("Vibrator service not available or no vibrator hardware found.")
        }
    }

    private val supportedSliderZenModes: Map<Int, Int> = mapOf( // Explicit type for map
        KEY_VALUE_TOTAL_SILENCE to Settings.Global.ZEN_MODE_NO_INTERRUPTIONS,
        KEY_VALUE_SILENT to Settings.Global.ZEN_MODE_OFF,
        KEY_VALUE_PRIORITY_ONLY to Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
        KEY_VALUE_VIBRATE to Settings.Global.ZEN_MODE_OFF,
        KEY_VALUE_NORMAL to Settings.Global.ZEN_MODE_OFF
    )

    private val supportedSliderRingModes: Map<Int, Int> = mapOf( // Explicit type for map
        KEY_VALUE_TOTAL_SILENCE to AudioManager.RINGER_MODE_NORMAL, // Note: Consider if SILENT is more appropriate for "Total Silence"
        KEY_VALUE_SILENT to AudioManager.RINGER_MODE_SILENT,
        KEY_VALUE_PRIORITY_ONLY to AudioManager.RINGER_MODE_NORMAL,
        KEY_VALUE_VIBRATE to AudioManager.RINGER_MODE_VIBRATE,
        KEY_VALUE_NORMAL to AudioManager.RINGER_MODE_NORMAL
    )

    private val supportedSliderHaptics: Map<Int, VibrationEffect?> = mapOf( // Explicit type for map
        KEY_VALUE_TOTAL_SILENCE to VibrationEffect.EFFECT_THUD?.let { VibrationEffect.get(it) }, // Use let to handle nullable EFFECT_THUD
        KEY_VALUE_SILENT to VibrationEffect.EFFECT_DOUBLE_CLICK?.let { VibrationEffect.get(it) },
        KEY_VALUE_PRIORITY_ONLY to VibrationEffect.EFFECT_POP?.let { VibrationEffect.get(it) },
        KEY_VALUE_VIBRATE to VibrationEffect.EFFECT_HEAVY_CLICK?.let { VibrationEffect.get(it) },
        KEY_VALUE_NORMAL to null // Or VibrationEffect.EFFECT_TICK, or keep null for no haptic
    )


    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.action != KeyEvent.ACTION_UP) { // Only handle ACTION_UP event
            return event
        }

        val deviceName = event.device?.name ?: ""

        if (
            deviceName != "oplus,hall_tri_state_key" &&
            deviceName != "oplus,tri-state-key" &&
            deviceName != "oplus_fp_input"
        ) {
            return event
        }

        val keyCodeValue = when (File("/proc/tristatekey/tri_state").readText().trim()) {
            "1" -> sharedPreferences.getString(ALERT_SLIDER_TOP_KEY, "0")!!.toInt()
            "2" -> sharedPreferences.getString(ALERT_SLIDER_MIDDLE_KEY, "1")!!.toInt()
            "3" -> sharedPreferences.getString(ALERT_SLIDER_BOTTOM_KEY, "2")!!.toInt()
            else -> return event // Or handle as needed, maybe return null to consume event?
        }

        needsRun = false
        handler.removeCallbacksAndMessages(null)

        if (prevKeyCode == KEY_VALUE_TOTAL_SILENCE && keyCodeValue != prevKeyCode) {
            // If previous mode was total silence, vibrate after setting ringer mode for it to take effect.
            // Exit total silence zen mode before setting ringer mode internally.
            val targetRingerMode = supportedSliderRingModes[keyCodeValue] ?: AudioManager.RINGER_MODE_NORMAL // Default if not found
            val targetZenMode = supportedSliderZenModes[keyCodeValue] ?: Settings.Global.ZEN_MODE_OFF // Default if not found

            notificationManager.setZenMode(targetZenMode, null, TAG)
            audioManager.ringerModeInternal = targetRingerMode

            doHapticFeedback(supportedSliderHaptics[keyCodeValue])

            needsRun = true
            handler.postDelayed({
                if (audioManager.ringerModeInternal != targetRingerMode && needsRun) {
                    audioManager.ringerModeInternal = targetRingerMode
                }
            }, 200) // 200ms delay to ensure ringer mode is set correctly
        } else {
            doHapticFeedback(supportedSliderHaptics[keyCodeValue])

            val targetRingerMode = supportedSliderRingModes[keyCodeValue] ?: AudioManager.RINGER_MODE_NORMAL
            val targetZenMode = supportedSliderZenModes[keyCodeValue] ?: Settings.Global.ZEN_MODE_OFF

            audioManager.ringerModeInternal = targetRingerMode
            notificationManager.setZenMode(targetZenMode, null, TAG)
        }


        if (sharedPreferences.getBoolean(MUTE_MEDIA_WITH_SILENT, false)) {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (keyCodeValue == KEY_VALUE_SILENT) {
                KeyHandler.setLastMediaLevel(packageContext, Math.round((currentVolume.toFloat() * 100f) / max.toFloat()).toInt()) // Call via Companion and toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            } else if (prevKeyCode == KEY_VALUE_SILENT && currentVolume == 0) {
                val lastVolume = KeyHandler.getLastMediaLevel(packageContext) // Call via Companion
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    Math.round((max.toFloat() * lastVolume.toFloat()) / 100f).toInt(), // toInt()
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }

        if (sharedPreferences.getBoolean(SLIDER_DIALOG_ENABLED, true)) { // Show slider dialog if enabled in preferences
            sendNotification(getPositionFromKeyCode(keyCodeValue), keyCodeValue)
        }

        prevKeyCode = keyCodeValue
        return null
    }

    override fun onPocketStateChanged(inPocket: Boolean) {
        // Do nothing
    }


    private fun doHapticFeedback(effect: VibrationEffect?) {
        if (vibrator != null && vibrator.hasVibrator() && effect != null) {
            vibrator.vibrate(effect)
        }
    }

    private fun sendNotification(position: Int, mode: Int) {
        val intent =
            Intent(SLIDER_UPDATE_ACTION).apply {
                putExtra("position", position)
                putExtra("mode", mode)
            }
        context.sendBroadcastAsUser(intent, UserHandle(UserHandle.USER_CURRENT))
    }

    private fun getPositionFromKeyCode(keyCodeValue: Int): Int {
        return when (keyCodeValue) {
            sharedPreferences.getString(ALERT_SLIDER_TOP_KEY, "0")!!.toInt() -> POSITION_TOP
            sharedPreferences.getString(ALERT_SLIDER_MIDDLE_KEY, "1")!!.toInt() -> POSITION_MIDDLE
            sharedPreferences.getString(ALERT_SLIDER_BOTTOM_KEY, "2")!!.toInt() -> POSITION_BOTTOM
            else -> 0 // Default case if key code doesn't match any position
        }
    }


    companion object {
        private const val TAG = "KeyHandler"

        // Slider key positions
        const val POSITION_TOP = 1
        const val POSITION_MIDDLE = 2
        const val POSITION_BOTTOM = 3

        const val SLIDER_UPDATE_ACTION = "org.lineageos.settings.device.UPDATE_SLIDER"

        // Preference keys
        private const val ALERT_SLIDER_TOP_KEY = "config_top_position"
        private const val ALERT_SLIDER_MIDDLE_KEY = "config_middle_position"
        private const val ALERT_SLIDER_BOTTOM_KEY = "config_bottom_position"
        private const val MUTE_MEDIA_WITH_SILENT = "config_mute_media"
        private const val SLIDER_DIALOG_ENABLED = "config_slider_dialog" // Preference to enable/disable dialog

        // Key Values - Slider modes as integer values
        const val KEY_VALUE_TOTAL_SILENCE = 0 // "Total Silence" slider mode
        const val KEY_VALUE_SILENT = AudioManager.RINGER_MODE_SILENT
        const val KEY_VALUE_PRIORITY_ONLY = 3 // "Priority Only" slider mode
        const val KEY_VALUE_VIBRATE = AudioManager.RINGER_MODE_VIBRATE
        const val KEY_VALUE_NORMAL = AudioManager.RINGER_MODE_NORMAL

        // ZEN constants - Make these public to be accessible from AlertSliderDialog
        public const val ZEN_PRIORITY_ONLY = 3 // Re-declare ZEN constants here, same values as before if needed for AlertSliderDialog
        public const val ZEN_TOTAL_SILENCE = 4
        public const val ZEN_ALARMS_ONLY = 5


        // Helper functions
        @JvmStatic // To be callable from Java if needed
        fun setLastMediaLevel(context: Context, level: Int) {
            val packageContext =
                context.createPackageContext(KeyHandler::class.java.getPackage()!!.name, 0)
            packageContext.getSharedPreferences(
                packageContext.packageName + "_preferences",
                Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
            ).edit().putInt("last_media_level", level).apply()
        }

        @JvmStatic
        fun getLastMediaLevel(context: Context): Int {
            val packageContext =
                context.createPackageContext(KeyHandler::class.java.getPackage()!!.name, 0)
            return packageContext.getSharedPreferences(
                packageContext.packageName + "_preferences",
                Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
            ).getInt("last_media_level", 50) // Default to 50 if not set
        }
    }
}