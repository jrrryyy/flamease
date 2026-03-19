package com.example.flamease

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore


class otp : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otp)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val etOtp1 = findViewById<EditText>(R.id.etOtp1)
        val etOtp2 = findViewById<EditText>(R.id.etOtp2)
        val etOtp3 = findViewById<EditText>(R.id.etOtp3)
        val etOtp4 = findViewById<EditText>(R.id.etOtp4)
        val etOtp5 = findViewById<EditText>(R.id.etOtp5)
        val etOtp6 = findViewById<EditText>(R.id.etOtp6)

        setupOtpAutoMove(etOtp1, etOtp2)
        setupOtpAutoMove(etOtp2, etOtp3)
        setupOtpAutoMove(etOtp3, etOtp4)
        setupOtpAutoMove(etOtp4, etOtp5)
        setupOtpAutoMove(etOtp5, etOtp6)

        btnConfirm.setOnClickListener {
            val enteredOtp = "${etOtp1.text}${etOtp2.text}${etOtp3.text}${etOtp4.text}${etOtp5.text}${etOtp6.text}"
            val email = intent.getStringExtra("email") ?: ""
            val serverOtp = intent.getStringExtra("otp") ?: ""
            val newPassword = intent.getStringExtra("newPassword") ?: ""

            if (enteredOtp == serverOtp) {
                // STEP 1: Search for the user where the "email" field matches (Same as createaccount check)
                db.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            // STEP 2: Get the document ID (the random UID seen in your console)
                            val documentId = querySnapshot.documents[0].id

                            // STEP 3: Update the password field
                            db.collection("users").document(documentId)
                                .update("password", newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Password Reset Successful!", Toast.LENGTH_SHORT).show()

                                    // Cleanup OTP from Singapore Realtime Database
                                    val emailKey = email.replace(".", "_")
                                    FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
                                        .getReference("PasswordResets").child(emailKey).removeValue()

                                    startActivity(Intent(this, Login::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    // This catches PERMISSION_DENIED if Firestore rules are wrong
                                    Toast.makeText(this, "Update Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Account not found in users collection.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Search Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Incorrect OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tryUsernameSearch(email: String, pass: String) {
        db.collection("users")
            .whereEqualTo("username", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val documentId = querySnapshot.documents[0].id
                    updateFirestorePassword(documentId, pass, email)
                } else {
                    // FINAL ATTEMPT: Document ID might be the email itself
                    db.collection("users").document(email).update("password", pass)
                        .addOnSuccessListener {
                            cleanupOtpAndFinish(email)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Account not found. Check Firestore field names!", Toast.LENGTH_LONG).show()
                        }
                }
            }
    }

    private fun updateFirestorePassword(docId: String, pass: String, email: String) {
        db.collection("users").document(docId)
            .update("password", pass)
            .addOnSuccessListener {
                cleanupOtpAndFinish(email)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Update Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cleanupOtpAndFinish(email: String) {
        Toast.makeText(this, "Password Updated!", Toast.LENGTH_SHORT).show()
        val emailKey = email.replace(".", "_")
        FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("PasswordResets").child(emailKey).removeValue()

        startActivity(Intent(this, Login::class.java))
        finish()
    }

    private fun setupOtpAutoMove(current: EditText, next: EditText) {
        current.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) next.requestFocus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}