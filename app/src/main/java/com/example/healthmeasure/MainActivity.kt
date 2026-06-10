package com.example.healthmeasure

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: WorkoutDatabaseHelper
    private lateinit var profileHelper: ProfileHelper

    private lateinit var tvWeeklyDistance: TextView
    private lateinit var tvWeeklyCalories: TextView
    private lateinit var tvWeeklyTime: TextView

    private lateinit var etProfileAge: EditText
    private lateinit var etProfileWeight: EditText
    private lateinit var etProfileHeight: EditText
    private lateinit var etTargetDistance: EditText
    private lateinit var etTargetCalories: EditText
    private lateinit var tvBmiStatus: TextView
    private lateinit var btnSaveProfile: Button

    private lateinit var rvWorkoutHistory: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvClearAllHistory: TextView
    private lateinit var btnStartWorkout: Button

    private lateinit var adapter: WorkoutAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Adjust for Edge to Edge system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = WorkoutDatabaseHelper(this)
        profileHelper = ProfileHelper(this)

        // Bind dashboard Views
        tvWeeklyDistance = findViewById(R.id.tvWeeklyDistance)
        tvWeeklyCalories = findViewById(R.id.tvWeeklyCalories)
        tvWeeklyTime = findViewById(R.id.tvWeeklyTime)

        // Bind profile settings Views
        etProfileAge = findViewById(R.id.etProfileAge)
        etProfileWeight = findViewById(R.id.etProfileWeight)
        etProfileHeight = findViewById(R.id.etProfileHeight)
        etTargetDistance = findViewById(R.id.etTargetDistance)
        etTargetCalories = findViewById(R.id.etTargetCalories)
        tvBmiStatus = findViewById(R.id.tvBmiStatus)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        // Bind history and button Views
        rvWorkoutHistory = findViewById(R.id.rvWorkoutHistory)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvClearAllHistory = findViewById(R.id.tvClearAllHistory)
        btnStartWorkout = findViewById(R.id.btnStartWorkout)

        setupProfileView()
        setupHistoryRecyclerView()

        // Button Click Listeners
        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }

        btnStartWorkout.setOnClickListener {
            val intent = Intent(this, HealthActivity::class.java)
            startActivity(intent)
        }

        tvClearAllHistory.setOnClickListener {
            confirmClearAllHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        loadWorkoutsAndStats()
        updateBmiDisplay()
    }

    private fun setupProfileView() {
        etProfileAge.setText(profileHelper.age.toString())
        etProfileWeight.setText(profileHelper.weight.toString())
        etProfileHeight.setText(profileHelper.heightCm.toString())
        etTargetDistance.setText(String.format(Locale.getDefault(), "%.1f", profileHelper.targetDistanceKm))
        etTargetCalories.setText(profileHelper.targetCaloriesKcal.toString())
        updateBmiDisplay()
    }

    private fun updateBmiDisplay() {
        val bmiVal = profileHelper.bmi
        val category = profileHelper.bmiCategory
        if (bmiVal > 0) {
            tvBmiStatus.text = String.format(Locale.getDefault(), "BMI: %.1f (%s)", bmiVal, category)
        } else {
            tvBmiStatus.text = "BMI: --"
        }
    }

    private fun saveUserProfile() {
        val ageStr = etProfileAge.text.toString().trim()
        val weightStr = etProfileWeight.text.toString().trim()
        val heightStr = etProfileHeight.text.toString().trim()
        val distStr = etTargetDistance.text.toString().trim()
        val calStr = etTargetCalories.text.toString().trim()

        if (ageStr.isEmpty() || weightStr.isEmpty() || heightStr.isEmpty() || distStr.isEmpty() || calStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageStr.toIntOrNull()
        val weight = weightStr.toIntOrNull()
        val height = heightStr.toIntOrNull()
        val dist = distStr.toFloatOrNull()
        val cal = calStr.toIntOrNull()

        if (age == null || age <= 0 || weight == null || weight <= 0 || height == null || height <= 0 || dist == null || dist <= 0 || cal == null || cal <= 0) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
            return
        }

        profileHelper.age = age
        profileHelper.weight = weight
        profileHelper.heightCm = height
        profileHelper.targetDistanceKm = dist
        profileHelper.targetCaloriesKcal = cal

        updateBmiDisplay()

        Toast.makeText(this, getString(R.string.profile_saved_success), Toast.LENGTH_SHORT).show()
        etProfileAge.clearFocus()
        etProfileWeight.clearFocus()
        etProfileHeight.clearFocus()
        etTargetDistance.clearFocus()
        etTargetCalories.clearFocus()
    }

    private fun setupHistoryRecyclerView() {
        rvWorkoutHistory.layoutManager = LinearLayoutManager(this)
        adapter = WorkoutAdapter(emptyList()) { session ->
            deleteWorkoutSession(session)
        }
        rvWorkoutHistory.adapter = adapter
    }

    private fun loadWorkoutsAndStats() {
        val workouts = dbHelper.getAllSessions()
        
        // Update History RecyclerView
        if (workouts.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvWorkoutHistory.visibility = View.GONE
            tvClearAllHistory.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvWorkoutHistory.visibility = View.VISIBLE
            tvClearAllHistory.visibility = View.VISIBLE
            adapter.updateData(workouts)
        }

        // Calculate Weekly Statistics
        var totalDistance = 0.0
        var totalCalories = 0
        var totalSeconds = 0L

        // Look at workouts from the last 7 days (or just sum total recorded for simplicity)
        val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        for (w in workouts) {
            if (w.timestamp >= cutoffTime) {
                totalDistance += w.distanceKm
                totalCalories += w.calories
                totalSeconds += w.durationSeconds
            }
        }

        // Bind stats to UI
        tvWeeklyDistance.text = String.format(Locale.getDefault(), "%.2f km", totalDistance)
        tvWeeklyCalories.text = String.format(Locale.getDefault(), "%d kcal", totalCalories)

        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        tvWeeklyTime.text = String.format(Locale.getDefault(), "%02dh %02dm", hours, mins)
    }

    private fun deleteWorkoutSession(session: WorkoutSession) {
        AlertDialog.Builder(this)
            .setTitle("Delete Workout")
            .setMessage("Are you sure you want to delete this workout session?")
            .setPositiveButton("Yes") { _, _ ->
                dbHelper.deleteSession(session.id)
                loadWorkoutsAndStats()
                Toast.makeText(this, "Workout deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun confirmClearAllHistory() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_title))
            .setMessage(getString(R.string.history_clear_confirm))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                dbHelper.clearAllSessions()
                loadWorkoutsAndStats()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }
}