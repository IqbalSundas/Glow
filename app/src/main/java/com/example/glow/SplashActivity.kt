package com.example.glow

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Set a timer to move to MainActivity after 3 seconds (3000 milliseconds)
        // If you really want it longer, change 3000 to 60000 (1 minute).
        Handler(Looper.getMainLooper()).postDelayed({
            // Start the main activity
            startActivity(Intent(this, MainActivity::class.java))
            // Close the splash activity so the user can't press back to it
            finish()
        }, 3000) // 3000 = 3 seconds
    }
}