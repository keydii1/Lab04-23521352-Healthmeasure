package com.example.healthmeasure

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var tvHeartRate: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnStartEnd: Button
    private lateinit var btnPause: Button

    private lateinit var exerciseClient: ExerciseClient
    private var isTracking = false
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health)

        // Ánh xạ View
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvCalories = findViewById(R.id.tvCalories)
        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        btnStartEnd = findViewById(R.id.btnStartEnd)
        btnPause = findViewById(R.id.btnPause)

        // Khởi tạo Health Client
        exerciseClient = HealthServices.getClient(this).exerciseClient

        // Yêu cầu cấp quyền trước khi làm bất cứ việc gì
        checkPermissions()

        btnStartEnd.setOnClickListener {
            if (!isTracking) {
                startExercise()
            } else {
                endExercise()
            }
        }

        btnPause.setOnClickListener {
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

            // Cập nhật Nhịp tim
            metrics.getData(DataType.HEART_RATE_BPM).let { hrData ->
                if (hrData.isNotEmpty()) {
                    tvHeartRate.text = String.format(Locale.getDefault(), "❤️ %d bpm", hrData.last().value.toInt())
                }
            }

            // Cập nhật Calo
            metrics.getData(DataType.CALORIES_TOTAL)?.let { calData ->
                tvCalories.text = String.format(Locale.getDefault(), "🔥 %d cal", calData.total.toInt())
            }

            // Cập nhật Quãng đường
            metrics.getData(DataType.DISTANCE_TOTAL)?.let { distData ->
                val distanceKm = distData.total / 1000.0
                tvDistance.text = String.format(Locale.getDefault(), "📈 %.2f km", distanceKm)
            }

            // Cập nhật thời gian
            val activeDuration = update.activeDurationCheckpoint?.activeDuration?.seconds ?: 0
            val minutes = activeDuration / 60
            val seconds = activeDuration % 60
            tvTime.text = String.format(Locale.getDefault(), "⏱ %02d:%02d", minutes, seconds)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {
            Toast.makeText(this@HealthActivity, "Lỗi đăng ký theo dõi: ${throwable.message}", Toast.LENGTH_SHORT).show()
        }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    private fun startExercise() {
        lifecycleScope.launch {
            try {
                // Định nghĩa bài tập chạy bộ
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

                // Cập nhật giao diện nút bấm
                isTracking = true
                isPaused = false
                btnStartEnd.text = "END"
                btnPause.isEnabled = true
                btnPause.text = "PAUSE"
                btnPause.setTextColor(android.graphics.Color.WHITE)

            } catch (e: Exception) {
                Toast.makeText(this@HealthActivity, "Không thể bắt đầu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pauseExercise() {
        lifecycleScope.launch {
            exerciseClient.pauseExerciseAsync().await()
            isPaused = true
            btnPause.text = "RESUME"
        }
    }

    private fun resumeExercise() {
        lifecycleScope.launch {
            exerciseClient.resumeExerciseAsync().await()
            isPaused = false
            btnPause.text = "PAUSE"
        }
    }

    private fun endExercise() {
        lifecycleScope.launch {
            exerciseClient.endExerciseAsync().await()
            exerciseClient.clearUpdateCallbackAsync(exerciseUpdateCallback).await()

            isTracking = false
            isPaused = false
            btnStartEnd.text = "START"
            btnPause.isEnabled = false
            btnPause.text = "PAUSE"
            btnPause.setTextColor(android.graphics.Color.GRAY)

            // Reset dữ liệu UI
            tvTime.text = "⏱ 00:00"
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}