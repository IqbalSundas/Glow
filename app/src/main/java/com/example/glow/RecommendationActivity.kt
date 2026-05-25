package com.example.glow

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.glow.data.CheckInDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecommendationActivity : AppCompatActivity() {

    private val db by lazy { CheckInDatabase.getDatabase(this) }
    private var checkInId: Int = -1
    private var state: String = ""

    // Track which activities were done
    private val completedActivities = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommendations)

        // Get state from MainActivity
        state = intent.getStringExtra("STATE") ?: "RESTED"

        val tvRecTitle = findViewById<TextView>(R.id.tvRecTitle)
        val tvRecSubtitle = findViewById<TextView>(R.id.tvRecSubtitle)
        val btnStartBreathing = findViewById<Button>(R.id.btnStartBreathing)
        val tvBreathingStatus = findViewById<TextView>(R.id.tvBreathingStatus)
        val btnPlayGame = findViewById<Button>(R.id.btnPlayGame)
        val btnStretch = findViewById<Button>(R.id.btnStretch)
        val btnFinish = findViewById<Button>(R.id.btnFinishActivities)

        // Fetch the latest ID from the database safely
        CoroutineScope(Dispatchers.IO).launch {
            val latestCheckIn = db.checkInDao().getLatestCheckIn()
            checkInId = latestCheckIn.id
        }

        // Set dynamic text based on state
        when (state) {
            "STRESSED" -> {
                tvRecTitle.text = "🔴 Let's Calm Down"
                tvRecSubtitle.text = "Stress detected. Try these relaxing activities."
            }
            "FATIGUED" -> {
                tvRecTitle.text = "🟡 Time to Recharge"
                tvRecSubtitle.text = "Fatigue detected. Take a short break."
            }
            else -> { // RESTED
                tvRecTitle.text = "🟢 You're In The Zone"
                tvRecSubtitle.text = "You're rested! Maintain this energy."
            }
        }

        // Breathing Timer Logic
        btnStartBreathing.setOnClickListener {
            completedActivities.add("Breathing")
            btnStartBreathing.isEnabled = false
            tvBreathingStatus.text = "Breathe... 15s"

            object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    tvBreathingStatus.text = "Breathe... ${millisUntilFinished / 1000}s"
                }
                override fun onFinish() {
                    tvBreathingStatus.text = "Done ✓"
                }
            }.start()
        }

        // Game / Distraction Logic
        btnPlayGame.setOnClickListener {
            completedActivities.add("Mental Reset")
            btnPlayGame.isEnabled = false
            btnPlayGame.text = "Done ✓"
        }

        // Stretch Logic
        btnStretch.setOnClickListener {
            completedActivities.add("Power Stretch")
            btnStretch.isEnabled = false
            btnStretch.text = "Done ✓"
        }

        // Save & Finish
        btnFinish.setOnClickListener {
            if (checkInId != -1) {
                val activitiesString = completedActivities.joinToString(separator = "|")
                CoroutineScope(Dispatchers.IO).launch {
                    db.checkInDao().updateActivities(checkInId, activitiesString)
                }
                Toast.makeText(this, "Session Saved! Great job.", Toast.LENGTH_SHORT).show()
                finish() // Closes this screen and goes back to the main camera
            } else {
                finish()
            }
        }
    }
}