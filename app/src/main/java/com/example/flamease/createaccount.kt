package com.example.flamease

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.TextInputLayout
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Message

class createaccount : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val REQ_ONE_TAP = 2
    private val TAG = "CreateAccount"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_createaccount)

        val scrollView = findViewById<ScrollView>(R.id.mainScrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft, view.paddingTop, view.paddingRight,
                if (imeInsets.bottom > 0) imeInsets.bottom else navInsets.bottom
            )
            insets
        }

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etFirstName        = findViewById<EditText>(R.id.etFirstName)
        val etLastName         = findViewById<EditText>(R.id.etLastName)
        val etEmail            = findViewById<EditText>(R.id.etEmail)
        val etStudentId        = findViewById<EditText>(R.id.studentId)
        val etPassword         = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword  = findViewById<EditText>(R.id.etConfirmPassword)
        val btnConfirm         = findViewById<Button>(R.id.btnConfirm)
        val rgRoles            = findViewById<RadioGroup>(R.id.rgRoles)
        val passLayout         = findViewById<TextInputLayout>(R.id.passwordLayout)
        val confirmPassLayout  = findViewById<TextInputLayout>(R.id.confirmPasswordLayout)
        val googleBtn          = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogleSignIn)
        val tvStrength         = findViewById<TextView>(R.id.tvPasswordStrength)

        for (i in 0 until googleBtn.childCount) {
            val v = googleBtn.getChildAt(i)
            if (v is TextView) { v.text = "Sign up with Google"; break }
        }

        googleBtn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, REQ_ONE_TAP)
            }
        }

        findViewById<TextView>(R.id.txtLogin).setOnClickListener {
            startActivity(Intent(this, Login::class.java)); finish()
        }

        setupPasswordWatcher(etPassword, passLayout, tvStrength)
        setupConfirmPasswordWatcher(etConfirmPassword, confirmPassLayout)

        val bottomFields = listOf(etPassword, etConfirmPassword)
        for (field in bottomFields) {
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    scrollView.postDelayed({
                        scrollView.smoothScrollTo(0, scrollView.getChildAt(0).height)
                    }, 300)
                }
            }
        }

        btnConfirm.setOnClickListener {
            if (validateInputs(etFirstName, etLastName, etStudentId, etEmail, etPassword, etConfirmPassword, passLayout, confirmPassLayout)) {

                // Change text and disable to prevent multiple clicks
                btnConfirm.text = "Sending OTP..."
                btnConfirm.isEnabled = false

                val email     = etEmail.text.toString().trim()
                val idNumber  = etStudentId.text.toString().trim()
                val firstName = etFirstName.text.toString().trim()
                val lastName  = etLastName.text.toString().trim()
                val password  = etPassword.text.toString()
                val role      = if (rgRoles.checkedRadioButtonId == R.id.rbStudent) "Student" else "Instructor"

                checkDuplicatesAndProceed(email, idNumber, firstName, lastName, password, role)
            }
        }
    }

    private fun setupPasswordWatcher(editText: EditText, layout: TextInputLayout, strengthView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                layout.error = null
                val password = s.toString()
                if (password.isEmpty()) strengthView.visibility = View.GONE
                else { strengthView.visibility = View.VISIBLE; updateStrengthIndicator(password, strengthView) }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupConfirmPasswordWatcher(editText: EditText, layout: TextInputLayout) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { layout.error = null }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateStrengthIndicator(password: String, strengthView: TextView) {
        val commonPasswords = listOf("12345678", "password", "qwertyuiop", "11111111", "aaaaaaaa", "mmmmmmmm")
        when {
            password.length < 8                              -> { strengthView.text = "Strength: Too short (Min 8)";           strengthView.setTextColor(Color.RED) }
            password.length > 64                             -> { strengthView.text = "Strength: Too long (Max 64)";            strengthView.setTextColor(Color.RED) }
            password.contains(" ")                           -> { strengthView.text = "Strength: Spaces not allowed";           strengthView.setTextColor(Color.RED) }
            commonPasswords.contains(password.lowercase())  -> { strengthView.text = "Strength: Extremely Weak (Common)";      strengthView.setTextColor(Color.RED) }
            isStrongPassword(password)                       -> { strengthView.text = "Strength: Strong";                       strengthView.setTextColor(Color.parseColor("#2ECC71")) }
            else                                             -> { strengthView.text = "Strength: Medium (Add numbers & symbols)"; strengthView.setTextColor(Color.parseColor("#F1C40F")) }
        }
    }

    private fun isStrongPassword(password: String): Boolean {
        val hasDigit   = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return password.length in 8..64 && hasDigit && hasSpecial && !password.contains(" ")
    }

    private fun validateInputs(
        firstName: EditText, lastName: EditText, studentId: EditText,
        email: EditText, pass: EditText, confirmPass: EditText,
        passLayout: TextInputLayout, confirmPassLayout: TextInputLayout
    ): Boolean {
        var isValid = true
        val commonPasswords = listOf("12345678", "password", "mmmmmmmm", "11111111", "aaaaaaaa")
        val fNameText   = firstName.text.toString()
        val lNameText   = lastName.text.toString()
        val emailText   = email.text.toString().trim()
        val idText      = studentId.text.toString().trim()
        val passText    = pass.text.toString()
        val confirmText = confirmPass.text.toString()

        if (fNameText.isBlank()) { firstName.error = "Required"; isValid = false }
        if (lNameText.isBlank()) { lastName.error  = "Required"; isValid = false }

        val idPattern = Regex("^(\\d{2}-\\d{4}-\\d{6})|(\\d{2}-\\d{2}-\\d{4}-\\d{6})$")
        if (!idText.matches(idPattern)) { studentId.error = "Invalid format"; isValid = false }

        val emailPattern = Regex("^[a-zA-Z0-9]+\\.[a-zA-Z0-9]+\\.up@phinmaed\\.com$")
        if (!emailText.matches(emailPattern)) { email.error = "Use Phinma email"; isValid = false }

        when {
            passText.length < 8                             -> { passLayout.error = "Minimum 8 characters required";          isValid = false }
            passText.length > 64                            -> { passLayout.error = "Maximum 64 characters allowed";           isValid = false }
            passText.contains(" ")                          -> { passLayout.error = "Spaces are not allowed";                  isValid = false }
            commonPasswords.contains(passText.lowercase())  -> { passLayout.error = "This password is too common";             isValid = false }
            !passText.any { it.isDigit() }                  -> { passLayout.error = "Must contain at least one number";        isValid = false }
            !passText.any { !it.isLetterOrDigit() }         -> { passLayout.error = "Must contain at least one special character"; isValid = false }
        }

        if (confirmText != passText) { confirmPassLayout.error = "Passwords do not match"; isValid = false }
        return isValid
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ONE_TAP) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val email = account?.email
                if (email != null && email.endsWith("@phinmaed.com")) {
                    firebaseAuthWithGoogle(account.idToken!!, email)
                } else {
                    googleSignInClient.signOut()
                    showErrorAlert("Access Restricted", "Please use your @phinmaed.com email.")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, email: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        // ✅ FIX: Check if email already has an account with email/password
        val auth = FirebaseAuth.getInstance()
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    showErrorAlert("Error", "Could not verify email")
                    return@addOnCompleteListener
                }

                val signInMethods = task.result?.signInMethods ?: emptyList()
                Log.d(TAG, "Sign-in methods for $email: $signInMethods")

                val hasEmailPassword = signInMethods.contains("password")
                val hasGoogle = signInMethods.contains("google.com")

                when {
                    // Case 1: Email account exists, user trying Google Sign-Up
                    // Should redirect to Login to properly link
                    hasEmailPassword && !hasGoogle -> {
                        showErrorAlert(
                            "Account Exists",
                            "This email already has an account. Please log in and link Google Sign-In from the Login screen."
                        )
                        auth.signOut()
                    }

                    // Case 2: Google account exists, just sign in
                    hasGoogle && !hasEmailPassword -> {
                        auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) checkUserAndRedirect(email, user)
                            } else {
                                showErrorAlert("Auth Failed", signInTask.exception?.message ?: "Unknown error")
                            }
                        }
                    }

                    // Case 3: Both exist - already linked
                    hasEmailPassword && hasGoogle -> {
                        auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) checkUserAndRedirect(email, user)
                            } else {
                                showErrorAlert("Auth Failed", signInTask.exception?.message ?: "Unknown error")
                            }
                        }
                    }

                    // Case 4: New Google user - proceed with registration
                    else -> {
                        auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) checkUserAndRedirect(email, user)
                            } else {
                                showErrorAlert("Auth Failed", signInTask.exception?.message ?: "Unknown error")
                            }
                        }
                    }
                }
            }
    }

    private fun checkUserAndRedirect(email: String, user: com.google.firebase.auth.FirebaseUser) {
        FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Account already exists — block re-registration
                    showErrorAlert(
                        "Account Exists",
                        "This account is already registered. Please go to the Login screen."
                    )
                    auth.signOut()
                    googleSignInClient.signOut()
                } else {
                    // New Google user — collect role and ID
                    showRegistrationDetailsDialog { selectedRole, enteredId ->
                        val firstName = user.displayName?.split(" ")?.firstOrNull() ?: ""
                        val lastName  = user.displayName?.split(" ")?.drop(1)?.joinToString(" ") ?: ""
                        val newUser = hashMapOf(
                            "firstName" to firstName,
                            "lastName"  to lastName,
                            "email"     to email,
                            "role"      to selectedRole,
                            "idNumber"  to enteredId,
                            "status"    to "approved",
                            "provider"  to "google"   // FIX: String, not listOf("google")
                        )
                        FirebaseFirestore.getInstance().collection("users").document(user.uid).set(newUser)
                            .addOnSuccessListener {
                                startActivity(Intent(this, faculty::class.java))
                                finish()
                            }
                    }
                }
            }
    }

    private fun showRegistrationDetailsDialog(onDetailsEntered: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Complete Your Profile")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val roles   = arrayOf("Student", "Instructor")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        val idInput = EditText(this).apply { hint = "ID Number"; inputType = InputType.TYPE_CLASS_TEXT }
        layout.addView(TextView(this).apply { text = "Select Role:" })
        layout.addView(spinner)
        layout.addView(idInput)
        builder.setView(layout)
        builder.setPositiveButton("Confirm") { _, _ ->
            onDetailsEntered(spinner.selectedItem.toString(), idInput.text.toString())
        }
        builder.show()
    }

    private fun checkDuplicatesAndProceed(email: String, id: String, fName: String, lName: String, pass: String, role: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").whereEqualTo("email", email).get().addOnSuccessListener { emailDocs ->
            if (!emailDocs.isEmpty) {
                findViewById<EditText>(R.id.etEmail).error = "Email already registered."
                return@addOnSuccessListener
            }
            db.collection("users").whereEqualTo("idNumber", id).get().addOnSuccessListener { idDocs ->
                if (!idDocs.isEmpty) {
                    findViewById<EditText>(R.id.studentId).error = "ID already registered."
                } else {
                    val generatedOTP = (100000..999999).random().toString()
                    saveOtpAndSendEmail(email, generatedOTP, pass, fName, lName, id, role)
                }
            }
        }
    }

    private fun saveOtpAndSendEmail(email: String, otp: String, pass: String, fName: String, lName: String, id: String, role: String) {
        val emailKey = email.replace(".", "_")
        FirebaseDatabase.getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("RegistrationOTPs").child(emailKey)
            .setValue(mapOf("otp" to otp, "timestamp" to System.currentTimeMillis()))
            .addOnSuccessListener {
                sendEmailWithOTP(email, otp)
                val intent = Intent(this, validation_otp::class.java).apply {
                    putExtra("email", email);     putExtra("password", pass)
                    putExtra("firstName", fName); putExtra("lastName", lName)
                    putExtra("idNumber", id);     putExtra("role", role)
                }
                startActivity(intent); finish()
            }
    }

    private fun sendEmailWithOTP(receiverEmail: String, otp: String) {
        val senderEmail = BuildConfig.EMAIL_SENDER
        val appPassword = BuildConfig.EMAIL_PASSWORD
        val props = Properties().apply {
            put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com"); put("mail.smtp.port", "587")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(senderEmail, appPassword)
        })
        Thread {
            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(senderEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiverEmail))
                    setSubject("FlameEase - Verify Your Account")
                    setText("Your verification code is: $otp")
                }
                Transport.send(message)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Email failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun showErrorAlert(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
