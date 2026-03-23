package com.example.flamease

import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class forget_password : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forget_password)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnSendLink = findViewById<Button>(R.id.btnResetPassword)
        val tvBack = findViewById<TextView>(R.id.etBack)

        tvBack.setOnClickListener { finish() }

        btnSendLink.setOnClickListener {
            val email = etEmail.text.toString().trim()

            // --- VALIDATION LOGIC ---

            // 1. Check if empty
            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            // 2. Check if it's a valid email format (e.g., contains @ and .)
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            // 3. STRICT PHINMA CHECK
            // This ensures the email ends exactly with @phinmaed.com
            if (!email.endsWith("@phinmaed.com", ignoreCase = true)) {
                etEmail.error = "Access denied. Use your @phinmaed.com email."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            // If all checks pass, proceed to Firebase
            sendFirebaseResetLink(email)
        }
    }

    private fun sendFirebaseResetLink(email: String) {
        // Show a simple toast or progress bar here if you like
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    finish() // Close activity and return to login
                } else {
                    val error = task.exception?.message ?: "Check your internet connection"
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
    }
}