package com.example.healthmeasure

import android.content.Context
import android.content.SharedPreferences

class ProfileHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var age: Int
        get() = prefs.getInt(KEY_AGE, DEFAULT_AGE)
        set(value) = prefs.edit().putInt(KEY_AGE, value).apply()

    var weight: Int
        get() = prefs.getInt(KEY_WEIGHT, DEFAULT_WEIGHT)
        set(value) = prefs.edit().putInt(KEY_WEIGHT, value).apply()

    val maxHeartRate: Int
        get() = 220 - age

    fun getHeartRateZone(bpm: Int): String {
        val max = maxHeartRate
        return when {
            bpm < max * 0.5 -> "Warm Up"
            bpm < max * 0.7 -> "Fat Burn"
            bpm < max * 0.85 -> "Cardio"
            else -> "Peak Zone"
        }
    }

    companion object {
        private const val PREFS_NAME = "user_profile_prefs"
        private const val KEY_AGE = "user_age"
        private const val KEY_WEIGHT = "user_weight"

        private const val DEFAULT_AGE = 25
        private const val DEFAULT_WEIGHT = 65
    }
}
