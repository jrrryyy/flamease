package com.example.flamease

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.FirebaseDatabase
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Message

class forget_password : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forget_password)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etconfirmPassword)
        val btnSendOtp = findViewById<Button>(R.id.btnResetPassword)

        // Layouts for eye-toggle control
        val passLayout = findViewById<TextInputLayout>(R.id.passwordLayout) // Ensure these IDs match your XML
        val confirmPassLayout = findViewById<TextInputLayout>(R.id.confirmPasswordLayout)

        findViewById<TextView>(R.id.etBack).setOnClickListener { finish() }

        // --- 1. TextWatchers to restore Eye Icon when typing ---
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                etNewPassword.error = null
                passLayout.isPasswordVisibilityToggleEnabled = true
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                etConfirmPassword.error = null
                confirmPassLayout.isPasswordVisibilityToggleEnabled = true
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSendOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etNewPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            // --- 2. Validation Logic (Consistent with Create Account) ---
            val passwordPattern = Regex("^[a-zA-Z0-9]{1,63}$")

            // Email Check
            if (!email.endsWith("@phinmaed.com")) {
                etEmail.error = "Use Phinma email"
                return@setOnClickListener
            }

            // Password Checks
            if (pass.isEmpty()) {
                passLayout.isPasswordVisibilityToggleEnabled = false
                etNewPassword.error = "Password is required"
                return@setOnClickListener
            } else if (pass.contains(" ")) {
                passLayout.isPasswordVisibilityToggleEnabled = false
                etNewPassword.error = "Spaces are not allowed"
                return@setOnClickListener
            } else if (pass.length > 63) {
                passLayout.isPasswordVisibilityToggleEnabled = false
                etNewPassword.error = "Max 63 characters allowed"
                return@setOnClickListener
            } else if (!pass.matches(passwordPattern)) {
                passLayout.isPasswordVisibilityToggleEnabled = false
                etNewPassword.error = "Only letters and numbers allowed"
                return@setOnClickListener
            }

            // Confirm Check
            if (pass != confirm) {
                confirmPassLayout.isPasswordVisibilityToggleEnabled = false
                etConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            // 3. Generate OTP and Navigate
            val generatedOTP = (100000..999999).random().toString()
            val intent = Intent(this, otp::class.java).apply {
                putExtra("email", email)
                putExtra("newPassword", pass)
                putExtra("otp", generatedOTP)
            }
            startActivity(intent)

            saveAndSendEmail(email, generatedOTP, pass)
            finish()
        }
    }

    // ... saveAndSendEmail and sendEmailWithOTP stay the same ...
    private fun saveAndSendEmail(email: String, otp: String, pass: String) {
        val emailKey = email.replace(".", "_")
        val db = FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("PasswordResets").child(emailKey)

        db.setValue(mapOf("otp" to otp)).addOnSuccessListener {
            sendEmailWithOTP(email, otp, pass)
        }
    }

    private fun sendEmailWithOTP(receiverEmail: String, otp: String, newPass: String) {
        val senderEmail = "flamease.config@gmail.com"
        val appPassword = "ffgj gntp fuzi hakd"

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(senderEmail, appPassword)
        })

        Thread {
            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(senderEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiverEmail))
                    setSubject("FlameEase - Reset Password OTP")
                    setText("Your OTP to reset your password is: $otp")
                }
                Transport.send(message)
                runOnUiThread {
                    Toast.makeText(applicationContext, "OTP Sent! Check your email.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}