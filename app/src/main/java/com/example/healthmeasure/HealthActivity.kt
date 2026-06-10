package com.example.healthmeasure

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.Locale

class HealthActivity : AppCompatActivity() {

    private lateinit var tvStatusLabel: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvHeartZone: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvTime: TextView
    
    private lateinit var ivHeartRateIcon: ImageView
    private lateinit var btnStartEnd: Button
    private lateinit var btnPause: Button

    private lateinit var exerciseClient: ExerciseClient
    private lateinit var dbHelper: WorkoutDatabaseHelper
    private lateinit var profileHelper: ProfileHelper

    private var isTracking = false
    private var isPaused = false

    // Statistics accumulated during the workout
    private var latestHeartRate = 0
    private var heartRateSum = 0
    private var heartRateCount = 0
    private var maxHeartRateRecord = 0
    private var totalCaloriesBurned = 0
    private var totalDistanceMeters = 0.0
    private var totalDurationSeconds = 0L
    private var startTimeMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health)

        dbHelper = WorkoutDatabaseHelper(this)
        profileHelper = ProfileHelper(this)

        // Bind UI Components
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvHeartZone = findViewById(R.id.tvHeartZone)
        tvCalories = findViewById(R.id.tvCalories)
        tvDistance = findViewById(R.id.tvDistance)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvTime = findViewById(R.id.tvTime)
        ivHeartRateIcon = findViewById(R.id.ivHeartRateIcon)
        btnStartEnd = findViewById(R.id.btnStartEnd)
        btnPause = findViewById(R.id.btnPause)

        // Initialize Health Services Client
        exerciseClient = HealthServices.getClient(this).exerciseClient

        // Ask for permissions on startup
        checkPermissions()

        btnStartEnd.setOnClickListener {
            triggerFeedback()
            if (!isTracking) {
                startExercise()
            } else {
                confirmEndExercise()
            }
        }

        btnPause.setOnClickListener {
            triggerFeedback()
            if (isPaused) {
                resumeExercise()
            } else {
                pauseExercise()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private val exerciseUpdateCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics

            // Update Heart Rate
            metrics.getData(DataType.HEART_RATE_BPM).let { hrData ->
                if (hrData.isNotEmpty()) {
                    val hr = hrData.last().value.toInt()
                    latestHeartRate = hr
                    heartRateSum += hr
                    heartRateCount++
                    if (hr > maxHeartRateRecord) {
                        maxHeartRateRecord = hr
                    }

                    tvHeartRate.text = String.format(Locale.getDefault(), "%d bpm", hr)
                    
                    // Display Heart Rate Zone
                    val zone = profileHelper.getHeartRateZone(hr)
                    tvHeartZone.text = zone
                    tvHeartZone.visibility = android.view.View.VISIBLE

                    // Update status text colors or background based on zone
                    when (zone) {
                        "Peak Zone" -> tvHeartZone.setBackgroundColor(ContextCompat.getColor(this@HealthActivity, R.color.metric_heart_rate))
                        "Cardio" -> tvHeartZone.setBackgroundColor(ContextCompat.getColor(this@HealthActivity, R.color.metric_calories))
                        "Fat Burn" -> tvHeartZone.setBackgroundColor(ContextCompat.getColor(this@HealthActivity, R.color.state_active))
                        else -> tvHeartZone.setBackgroundColor(ContextCompat.getColor(this@HealthActivity, R.color.metric_distance))
                    }

                    // Animate Heart Pulse
                    animateHeartRatePulse()
                }
            }

            // Update Calories
            metrics.getData(DataType.CALORIES_TOTAL)?.let { calData ->
                val cal = calData.total.toInt()
                totalCaloriesBurned = cal
                tvCalories.text = String.format(Locale.getDefault(), "%d kcal", cal)
            } ?: run {
                // Fallback Active Calories calculation: 1.03 * Weight (kg) * Distance (km)
                val distKm = totalDistanceMeters / 1000.0
                val estCal = (1.03 * profileHelper.weight * distKm).toInt()
                totalCaloriesBurned = estCal
                tvCalories.text = String.format(Locale.getDefault(), "%d kcal", estCal)
            }

            // Update Distance
            metrics.getData(DataType.DISTANCE_TOTAL)?.let { distData ->
                val distMeters = distData.total
                totalDistanceMeters = distMeters
                val distanceKm = distMeters / 1000.0
                tvDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceKm)
            }

            // Update Duration Stopwatch
            val activeDuration = update.activeDurationCheckpoint?.activeDuration?.seconds ?: 0
            totalDurationSeconds = activeDuration
            val minutes = activeDuration / 60
            val seconds = activeDuration % 60
            tvTime.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

            // Update Pace (Average Speed) MM:SS /km
            val distKm = totalDistanceMeters / 1000.0
            if (distKm > 0.01 && activeDuration > 0) {
                val paceSeconds = (activeDuration / distKm).toLong()
                val paceMins = paceSeconds / 60
                val paceSecs = paceSeconds % 60
                tvSpeed.text = String.format(Locale.getDefault(), "%02d:%02d /km", paceMins, paceSecs)
            } else {
                tvSpeed.text = "-- /km"
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {
            Toast.makeText(this@HealthActivity, getString(R.string.error_registration, throwable.message), Toast.LENGTH_SHORT).show()
        }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    private fun startExercise() {
        lifecycleScope.launch {
            try {
                val dataTypes = setOf(
                    DataType.HEART_RATE_BPM,
                    DataType.CALORIES_TOTAL,
                    DataType.DISTANCE_TOTAL
                )

                val config = ExerciseConfig(
                    exerciseType = ExerciseType.RUNNING,
                    dataTypes = dataTypes,
                    isAutoPauseAndResumeEnabled = false,
                    isGpsEnabled = true
                )

                exerciseClient.setUpdateCallback(exerciseUpdateCallback)
                exerciseClient.startExerciseAsync(config).await()

                // Keep screen on during workout session
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                isTracking = true
                isPaused = false
                startTimeMillis = System.currentTimeMillis()
                
                // Reset session variables
                heartRateSum = 0
                heartRateCount = 0
                maxHeartRateRecord = 0
                totalCaloriesBurned = 0
                totalDistanceMeters = 0.0
                totalDurationSeconds = 0L

                // Update UI state
                tvStatusLabel.text = "RUNNING"
                tvStatusLabel.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.state_active))
                btnStartEnd.text = "END"
                btnStartEnd.setBackgroundResource(R.drawable.button_danger)
                btnPause.isEnabled = true
                btnPause.text = "PAUSE"
                btnPause.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.white))

            } catch (e: Exception) {
                Toast.makeText(this@HealthActivity, getString(R.string.error_start, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pauseExercise() {
        lifecycleScope.launch {
            try {
                exerciseClient.pauseExerciseAsync().await()
                isPaused = true
                
                tvStatusLabel.text = "PAUSED"
                tvStatusLabel.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.state_paused))
                btnPause.text = "RESUME"
            } catch (e: Exception) {
                Toast.makeText(this@HealthActivity, "Lỗi dừng: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resumeExercise() {
        lifecycleScope.launch {
            try {
                exerciseClient.resumeExerciseAsync().await()
                isPaused = false

                tvStatusLabel.text = "RUNNING"
                tvStatusLabel.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.state_active))
                btnPause.text = "PAUSE"
            } catch (e: Exception) {
                Toast.makeText(this@HealthActivity, "Lỗi tiếp tục: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmEndExercise() {
        AlertDialog.Builder(this)
            .setTitle("End Workout Session")
            .setMessage("Are you sure you want to end and save your workout session?")
            .setPositiveButton("End & Save") { _, _ ->
                endExerciseAndSave()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endExerciseAndSave() {
        lifecycleScope.launch {
            try {
                exerciseClient.endExerciseAsync().await()
                exerciseClient.clearUpdateCallbackAsync(exerciseUpdateCallback).await()
            } catch (e: Exception) {
                // Ignore client errors on cleanup
            }

            // Remove keep screen on flag
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            isTracking = false
            isPaused = false

            // Save session to SQLite database
            val avgHr = if (heartRateCount > 0) heartRateSum / heartRateCount else 0
            val session = WorkoutSession(
                timestamp = startTimeMillis,
                durationSeconds = totalDurationSeconds,
                distanceKm = totalDistanceMeters / 1000.0,
                calories = totalCaloriesBurned,
                avgHeartRate = avgHr,
                maxHeartRate = maxHeartRateRecord
            )
            
            dbHelper.insertSession(session)
            
            // Show detailed summary dialog
            showWorkoutSummaryDialog(session)

            // Reset UI
            tvStatusLabel.text = "READY"
            tvStatusLabel.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.state_idle))
            tvTime.text = "⏱ 00:00"
            tvHeartRate.text = "-- bpm"
            tvCalories.text = "-- kcal"
            tvDistance.text = "-- km"
            tvSpeed.text = "-- /km"
            tvHeartZone.visibility = android.view.View.GONE
            
            btnStartEnd.text = "START"
            btnStartEnd.setBackgroundResource(R.drawable.button_primary)
            btnPause.isEnabled = false
            btnPause.text = "PAUSE"
            btnPause.setTextColor(ContextCompat.getColor(this@HealthActivity, R.color.state_idle))
        }
    }

    private fun showWorkoutSummaryDialog(session: WorkoutSession) {
        val minutes = session.durationSeconds / 60
        val seconds = session.durationSeconds % 60
        val durationStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        
        val summaryMessage = """
            ⏱ Duration: $durationStr
            📈 Distance: ${String.format(Locale.getDefault(), "%.2f km", session.distanceKm)}
            🔥 Calories: ${session.calories} kcal
            ❤️ Avg HR: ${if (session.avgHeartRate > 0) "${session.avgHeartRate} bpm" else "--"}
            🏆 Max HR: ${if (session.maxHeartRate > 0) "${session.maxHeartRate} bpm" else "--"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.workout_summary_title))
            .setMessage(summaryMessage)
            .setPositiveButton("Awesome") { dialog, _ ->
                dialog.dismiss()
                finish() // Go back to MainActivity dashboard
            }
            .setCancelable(false)
            .show()
    }

    private fun animateHeartRatePulse() {
        try {
            val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
            ivHeartRateIcon.startAnimation(pulseAnim)
        } catch (e: Exception) {
            // Safe fallback if animation loading fails
        }
    }

    private fun triggerFeedback() {
        // Haptic feedback (Vibrate)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            // Ignore feedback errors
        }

        // Audio feedback (short beep tone)
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (e: Exception) {
            // Ignore tone errors
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}