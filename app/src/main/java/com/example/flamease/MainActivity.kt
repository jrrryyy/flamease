package com.example.flamease

import com.example.flamease.Login
import com.example.flamease.createaccount
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {

    // 1. Declare FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
// Force Light Mode globally
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // 2. Initialize Firebase Auth
        val auth: FirebaseAuth = Firebase.auth

        // 3. Check SharedPreferences
        val sharedPreferences = getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
        val onboardingFinished = sharedPreferences.getBoolean("onboarding_finished", false)
        val isRemembered = sharedPreferences.getBoolean("rememberMe", false)
        val currentUser = Firebase.auth.currentUser

        // 4. AUTO-LOGIN: If remembered AND Firebase session is still active
        if (isRemembered && auth.currentUser != null) {
            val intent = Intent(this, faculty::class.java)
            startActivity(intent)
            finish() // Prevent going back to splash/onboarding
            return // Exit onCreate early so the UI doesn't load
        }
        if (onboardingFinished) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Linking to onboarding1
        val loginBtn = findViewById<Button>(R.id.btnGetStarted)
        loginBtn.setOnClickListener {
            val intent = Intent(this, onboarding1::class.java)
            startActivity(intent)
            finish()
        }

        // Linking to create account
        val loginText = findViewById<TextView>(R.id.tvSkip)
        loginText.setOnClickListener {
            val intent = Intent(this, createaccount::class.java)
            startActivity(intent)
            finish()
        }
    }
}