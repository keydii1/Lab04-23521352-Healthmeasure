package com.example.healthmeasure

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class WorkoutAdapter(
    private var sessions: List<WorkoutSession>,
    private val onDeleteClickListener: (WorkoutSession) -> Unit
) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

    class WorkoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvDistance: TextView = view.findViewById(R.id.tvItemDistance)
        val tvCalories: TextView = view.findViewById(R.id.tvItemCalories)
        val tvDuration: TextView = view.findViewById(R.id.tvItemDuration)
        val tvHeartRate: TextView = view.findViewById(R.id.tvItemHeartRate)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_history, parent, false)
        return WorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val session = sessions[position]
        
        // Date formatting
        val sdf = SimpleDateFormat("EEE, MMM dd, yyyy - HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(session.timestamp))
        
        // Distance
        holder.tvDistance.text = String.format(Locale.getDefault(), "%.2f km", session.distanceKm)
        
        // Calories
        holder.tvCalories.text = String.format(Locale.getDefault(), "%d kcal", session.calories)
        
        // Duration
        val minutes = session.durationSeconds / 60
        val seconds = session.durationSeconds % 60
        holder.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        
        // Heart Rate
        if (session.avgHeartRate > 0) {
            holder.tvHeartRate.text = String.format(Locale.getDefault(), "%d bpm", session.avgHeartRate)
        } else {
            holder.tvHeartRate.text = "-- bpm"
        }

        // Delete click
        holder.btnDelete.setOnClickListener {
            onDeleteClickListener(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateData(newSessions: List<WorkoutSession>) {
        this.sessions = newSessions
        notifyDataSetChanged()
    }
}
