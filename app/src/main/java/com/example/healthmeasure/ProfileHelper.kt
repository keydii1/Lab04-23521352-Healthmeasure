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

    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    var targetDistanceKm: Float
        get() = prefs.getFloat(KEY_TARGET_DISTANCE, DEFAULT_TARGET_DISTANCE)
        set(value) = prefs.edit().putFloat(KEY_TARGET_DISTANCE, value).apply()

    var targetCaloriesKcal: Int
        get() = prefs.getInt(KEY_TARGET_CALORIES, DEFAULT_TARGET_CALORIES)
        set(value) = prefs.edit().putInt(KEY_TARGET_CALORIES, value).apply()

    val maxHeartRate: Int
        get() = 220 - age

    val bmi: Float
        get() {
            val heightM = heightCm / 100.0f
            return if (heightM > 0) weight / (heightM * heightM) else 0.0f
        }

    val bmiCategory: String
        get() {
            val value = bmi
            return when {
                value < 18.5f -> "Underweight"
                value < 25.0f -> "Normal weight"
                value < 30.0f -> "Overweight"
                else -> "Obese"
            }
        }

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
        private const val KEY_HEIGHT = "user_height"
        private const val KEY_TARGET_DISTANCE = "user_target_distance"
        private const val KEY_TARGET_CALORIES = "user_target_calories"

        private const val DEFAULT_AGE = 25
        private const val DEFAULT_WEIGHT = 65
        private const val DEFAULT_HEIGHT = 170
        private const val DEFAULT_TARGET_DISTANCE = 5.0f
        private const val DEFAULT_TARGET_CALORIES = 300
    }
}
