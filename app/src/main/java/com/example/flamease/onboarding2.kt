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


class onboarding2 : AppCompatActivity() {
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
        setContentView(R.layout.activity_onboarding2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Navigation to create account
        findViewById<Button>(R.id.btnFinish).setOnClickListener {
            startActivity(Intent(this, createaccount::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
        startActivity(Intent(this, createaccount::class.java))
        finish()
        }
    }
    }
