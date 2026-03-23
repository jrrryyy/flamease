package com.example.flamease

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Message

class validation_otp : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var otpExpired = false
    private lateinit var tvResend: TextView
    private var currentServerOtp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        enableEdgeToEdge()
        setContentView(R.layout.activity_validation_otp)

        val auth = FirebaseAuth.getInstance()
        tvResend = findViewById(R.id.tvResend)

        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val idNumber = intent.getStringExtra("idNumber") ?: ""
        val role = intent.getStringExtra("role") ?: ""
        val fName = intent.getStringExtra("firstName") ?: ""
        val lName = intent.getStringExtra("lastName") ?: ""

        val etOtp1 = findViewById<EditText>(R.id.etOtp1)
        val etOtp2 = findViewById<EditText>(R.id.etOtp2)
        val etOtp3 = findViewById<EditText>(R.id.etOtp3)
        val etOtp4 = findViewById<EditText>(R.id.etOtp4)
        val etOtp5 = findViewById<EditText>(R.id.etOtp5)
        val etOtp6 = findViewById<EditText>(R.id.etOtp6)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val otpBoxes = arrayOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)

        setupOtpLogic(otpBoxes)
        fetchOtpFromDatabase(email)

        tvResend.setOnClickListener {
            if (otpExpired) sendNewOtp(email)
        }

        btnConfirm.setOnClickListener {
            val enteredOtp = otpBoxes.joinToString("") { it.text.toString() }

            if (otpExpired) {
                Toast.makeText(this, "Code expired. Please resend.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredOtp == currentServerOtp && currentServerOtp.isNotEmpty()) {
                // --- ADD THESE LINES ---
                btnConfirm.text = "Creating Account..."
                btnConfirm.isEnabled = false // Prevents double-clicking
                // -----------------------

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            saveUserToFirestore(auth.currentUser?.uid ?: "", fName, lName, email, idNumber, role)
                        } else {
                            // --- RESET IF IT FAILS ---
                            btnConfirm.text = "Confirm"
                            btnConfirm.isEnabled = true
                            // --------------------------
                            Toast.makeText(this, "Auth Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Invalid OTP.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOtpLogic(boxes: Array<EditText>) {
        for (i in boxes.indices) {
            boxes[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val input = s.toString()
                    if (input.length > 1) {
                        val digits = input.filter { it.isDigit() }
                        for (j in 0 until digits.length.coerceAtMost(boxes.size)) {
                            boxes[j].setText(digits[j].toString())
                        }
                        val nextFocus = if (digits.length < boxes.size) digits.length else boxes.size - 1
                        boxes[nextFocus].requestFocus()
                        boxes[nextFocus].setSelection(boxes[nextFocus].text.length)
                    } else if (input.isNotEmpty() && i < boxes.size - 1) {
                        boxes[i + 1].requestFocus()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            boxes[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (boxes[i].text.isEmpty() && i > 0) {
                        boxes[i - 1].requestFocus()
                        boxes[i - 1].setText("")
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun fetchOtpFromDatabase(email: String) {
        val emailKey = email.replace(".", "_")
        val dbRef = FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("RegistrationOTPs").child(emailKey)
        dbRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                currentServerOtp = snapshot.child("otp").value.toString()
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val otpDuration = 100 * 1000L
                val currentTime = System.currentTimeMillis()
                if (currentTime - timestamp > otpDuration) {
                    setOtpExpiredUI()
                } else {
                    startOtpTimer(otpDuration - (currentTime - timestamp))
                }
            }
        }
    }

    private fun startOtpTimer(duration: Long) {
        otpExpired = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(ms: Long) {
                tvResend.text = "Resend Code in ${ms / 1000}s"
                tvResend.isClickable = false
                tvResend.setTextColor(Color.GRAY)
            }
            override fun onFinish() { setOtpExpiredUI() }
        }.start()
    }

    private fun setOtpExpiredUI() {
        otpExpired = true
        tvResend.text = "Resend Code"
        tvResend.isClickable = true
        tvResend.setTextColor(Color.parseColor("#2ECC71"))
    }

    private fun sendNewOtp(email: String) {
        val newOtp = (100000..999999).random().toString()
        val emailKey = email.replace(".", "_")
        FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("RegistrationOTPs").child(emailKey)
            .setValue(mapOf("otp" to newOtp, "timestamp" to System.currentTimeMillis()))
            .addOnSuccessListener {
                currentServerOtp = newOtp
                sendEmailLocally(email, newOtp)
                startOtpTimer(100 * 1000L)
                Toast.makeText(this, "New code sent!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendEmailLocally(receiverEmail: String, otp: String) {
        val senderEmail = BuildConfig.EMAIL_SENDER
        val appPassword = BuildConfig.EMAIL_PASSWORD
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
                    setSubject("FlameEase - Verification Code")
                    setText("Your code is: $otp")
                }
                Transport.send(message)
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun saveUserToFirestore(uid: String, f: String, l: String, e: String, id: String, r: String) {
        val userData = hashMapOf(
            "firstName" to f,
            "lastName" to l,
            "email" to e,
            "idNumber" to id,
            "role" to r,
            "status" to "approved",
            "provider" to "email"   // FIX: String, not listOf("email")
        )
        FirebaseFirestore.getInstance().collection("users").document(uid).set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_LONG).show()
                val emailKey = e.replace(".", "_")
                FirebaseDatabase.getInstance()
                    .getReference("RegistrationOTPs").child(emailKey).removeValue()
                startActivity(Intent(this, Login::class.java))
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
