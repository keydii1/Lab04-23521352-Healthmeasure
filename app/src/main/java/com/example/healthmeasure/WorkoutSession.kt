package com.example.healthmeasure

data class WorkoutSession(
    val id: Long = 0,
    val timestamp: Long,         // epoch milliseconds
    val durationSeconds: Long,   // duration in seconds
    val distanceKm: Double,      // distance in kilometers
    val calories: Int,           // active calories burned
    val avgHeartRate: Int,       // average BPM
    val maxHeartRate: Int        // maximum BPM
)
