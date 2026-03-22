package com.example.flamease

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Settings : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val txtNameDisplay = findViewById<TextView>(R.id.txtNameDisplay)
        val txtSettingsRole = findViewById<TextView>(R.id.Settings_roles)
        val txtStudentId = findViewById<TextView>(R.id.txtStudentId)

        // Fetch User Data from Firestore
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val fName = document.getString("firstName") ?: ""
                        val lName = document.getString("lastName") ?: ""
                        val role = document.getString("role") ?: ""
                        val idNum = document.getString("idNumber") ?: ""

                        txtNameDisplay.text = "$fName $lName".uppercase()
                        txtSettingsRole.text = role
                        txtStudentId.text = idNum
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        }

        // Navigation
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.home).setOnClickListener {
            val intent = Intent(this, faculty::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnRequest).setOnClickListener {
            val intent = Intent(this, request::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            val intent = Intent(this, notifications::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnPrivacy).setOnClickListener {
            startActivity(Intent(this, privacy_policy::class.java))
        }

        findViewById<LinearLayout>(R.id.btnHelp).setOnClickListener {
            startActivity(Intent(this, help_support::class.java))
        }

        // Updated Logout Logic with Confirmation Message
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmation(auth)
        }
    }

    private fun showLogoutConfirmation(auth: FirebaseAuth) {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Logout") { _, _ ->
                // Perform Logout logic
                auth.signOut()
                val sharedPrefs = getSharedPreferences("FlameEasePrefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().clear().apply()

                val intent = Intent(this, Login::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}