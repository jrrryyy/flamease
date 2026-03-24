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
import com.google.firebase.firestore.ListenerRegistration

class Settings : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // We keep a reference to the listener so we can kill it manually if needed
    private var profileListener: ListenerRegistration? = null

    override fun onResume() {
        super.onResume()
        // ✅ Only trigger if user is actually logged in
        if (auth.currentUser != null) {
            BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val userId = auth.currentUser?.uid

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val txtNameDisplay = findViewById<TextView>(R.id.txtNameDisplay)
        val txtSettingsRole = findViewById<TextView>(R.id.Settings_roles)
        val txtStudentId = findViewById<TextView>(R.id.txtStudentId)

        // --- Fetch User Data Safely ---
        if (userId != null) {
            // Using a ListenerRegistration allows us to stop the request immediately on logout
            val docRef = db.collection("users").document(userId)

            docRef.get().addOnCompleteListener { task ->
                // ✅ CRITICAL: Check if activity is finishing or user logged out before processing
                if (isFinishing || isDestroyed || auth.currentUser == null) return@addOnCompleteListener

                if (task.isSuccessful) {
                    val document = task.result
                    if (document != null && document.exists()) {
                        val fName = document.getString("firstName") ?: ""
                        val lName = document.getString("lastName") ?: ""
                        val role = document.getString("role") ?: ""
                        val idNum = document.getString("idNumber") ?: ""

                        txtNameDisplay.text = "$fName $lName".uppercase()
                        txtSettingsRole.text = role
                        txtStudentId.text = idNum
                    }
                } else {
                    // Only show error if we didn't just log out
                    if (auth.currentUser != null) {
                        Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // --- Navigation ---
        findViewById<TextView>(R.id.btnBack).setOnClickListener { navigateToFaculty() }
        findViewById<LinearLayout>(R.id.home).setOnClickListener { navigateToFaculty() }

        findViewById<LinearLayout>(R.id.btnRequest).setOnClickListener {
            startActivity(Intent(this, request::class.java))
        }

        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            startActivity(Intent(this, notifications::class.java))
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun navigateToFaculty() {
        val intent = Intent(this, faculty::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setCancelable(false) // Force them to choose
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performLogout() {
        // 1. Clear SharedPreferences
        val sharedPrefs = getSharedPreferences("FlameEasePrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        // 2. Sign out from Firebase
        auth.signOut()

        // 3. Clear Activity Stack and move to Login
        val intent = Intent(this, Login::class.java)
        // This ensures NO other activities stay open in the background
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // 4. "Nuclear Option": Kill this activity and all others in the task immediately
        finishAffinity()
    }

    override fun onBackPressed() {
        navigateToFaculty()
    }
}