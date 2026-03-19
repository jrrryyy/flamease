package com.example.flamease

import com.example.flamease.createaccount
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore


class onboarding1 : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Session Check
        val auth: FirebaseAuth = Firebase.auth
        val sharedPreferences = getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
        if (sharedPreferences.getBoolean("rememberMe", false) && auth.currentUser != null) {
            startActivity(Intent(this, faculty::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding1)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Navigation to onboarding2
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, onboarding2::class.java))
            // Uses standard Android slide-in from right, slide-out to left
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        // Navigation to create account
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            startActivity(Intent(this, createaccount::class.java))
            finish()
        }
    }
}