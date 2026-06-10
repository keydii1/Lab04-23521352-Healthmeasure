package com.example.healthmeasure

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WorkoutDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_WORKOUTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_DURATION INTEGER,
                $COLUMN_DISTANCE REAL,
                $COLUMN_CALORIES INTEGER,
                $COLUMN_AVG_HR INTEGER,
                $COLUMN_MAX_HR INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORKOUTS")
        onCreate(db)
    }

    fun insertSession(session: WorkoutSession): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, session.timestamp)
            put(COLUMN_DURATION, session.durationSeconds)
            put(COLUMN_DISTANCE, session.distanceKm)
            put(COLUMN_CALORIES, session.calories)
            put(COLUMN_AVG_HR, session.avgHeartRate)
            put(COLUMN_MAX_HR, session.maxHeartRate)
        }
        val id = db.insert(TABLE_WORKOUTS, null, values)
        db.close()
        return id
    }

    fun getAllSessions(): List<WorkoutSession> {
        val list = mutableListOf<WorkoutSession>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_WORKOUTS ORDER BY $COLUMN_TIMESTAMP DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION))
                val distance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE))
                val calories = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CALORIES))
                val avgHr = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AVG_HR))
                val maxHr = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MAX_HR))

                list.add(WorkoutSession(id, timestamp, duration, distance, calories, avgHr, maxHr))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deleteSession(id: Long): Int {
        val db = this.writableDatabase
        val result = db.delete(TABLE_WORKOUTS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }

    fun clearAllSessions() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_WORKOUTS")
        db.close()
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "HealthTracker.db"
        
        const val TABLE_WORKOUTS = "workouts"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DURATION = "duration"
        const val COLUMN_DISTANCE = "distance"
        const val COLUMN_CALORIES = "calories"
        const val COLUMN_AVG_HR = "avg_heart_rate"
        const val COLUMN_MAX_HR = "max_heart_rate"
    }
}
