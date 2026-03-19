package com.example.flamease

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
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
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val REQ_ONE_TAP = 2
    private val TAG = "LoginDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val createAccount = findViewById<TextView>(R.id.txtCreateacc)
        val forgetPassword = findViewById<TextView>(R.id.txtForgetPassword)
        val googleBtn = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogleSignIn)
        // Change Google Button Text
        for (i in 0 until googleBtn.childCount) {
            val v = googleBtn.getChildAt(i)
            if (v is TextView) {
                v.text = "Sign up with Google"
                break
            }
        }

        // Google Sign-In Button Listener
        googleBtn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, REQ_ONE_TAP)
            }
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                etPassword.error = null
                passwordLayout.isPasswordVisibilityToggleEnabled = true
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        createAccount.setOnClickListener {
            startActivity(Intent(this, createaccount::class.java))
        }

        forgetPassword.setOnClickListener {
            startActivity(Intent(this, forget_password::class.java))
        }

        sharedPreferences = getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
        val cbRememberMe = findViewById<CheckBox>(R.id.checkBox)

        if (sharedPreferences.getBoolean("rememberMe", false) && auth.currentUser != null) {
            startActivity(Intent(this, faculty::class.java))
            finish()
        }

        btnLogin.setOnClickListener {
            if (validateInputs(etEmail, etPassword, passwordLayout)) {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            handleSuccessfulLogin(cbRememberMe.isChecked)
                        } else {
                            passwordLayout.isPasswordVisibilityToggleEnabled = false
                            etPassword.error = "Incorrect email or password"
                            Toast.makeText(this, task.exception?.message ?: "Login failed.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ONE_TAP) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val email = account.email
                    if (email != null && email.endsWith("@phinmaed.com")) {
                        firebaseAuthWithGoogle(account.idToken!!, email)
                    } else {
                        googleSignInClient.signOut()
                        showErrorAlert("Access Restricted", "Please use your official @phinmaed.com email.")
                    }
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: ${e.statusCode}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, email: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) checkUserAndRedirect(email, user)
            } else {
                showErrorAlert("Auth Failed", task.exception?.localizedMessage ?: "Error")
            }
        }
    }

    private fun checkUserAndRedirect(email: String, user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                handleSuccessfulLogin(true) // Default remember me for Google
            } else {
                showRegistrationDetailsDialog { selectedRole, enteredId ->
                    val firstName = user.displayName?.split(" ")?.firstOrNull() ?: ""
                    val lastName = user.displayName?.split(" ")?.drop(1)?.joinToString(" ") ?: ""

                    val newUser = hashMapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "email" to email,
                        "role" to selectedRole,
                        "idNumber" to enteredId
                    )

                    db.collection("users").document(user.uid).set(newUser).addOnSuccessListener {
                        handleSuccessfulLogin(true)
                    }
                }
            }
        }
    }

    private fun showRegistrationDetailsDialog(onDetailsEntered: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Complete Your Profile").setCancelable(false)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val roles = arrayOf("Student", "Instructor")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        val idInput = EditText(this).apply {
            hint = "ID Number (XX-XXXX-XXXXXX)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        layout.addView(TextView(this).apply { text = "Select Role:" })
        layout.addView(spinner)
        layout.addView(TextView(this).apply { height = 20 })
        layout.addView(idInput)
        builder.setView(layout)

        builder.setPositiveButton("Confirm") { _, _ -> }
        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredId = idInput.text.toString().trim()
            val idPattern = Regex("^(\\d{2}-\\d{4}-\\d{6})|(\\d{2}-\\d{2}-\\d{4}-\\d{6})$")

            if (enteredId.matches(idPattern)) {
                dialog.dismiss()
                onDetailsEntered(spinner.selectedItem.toString(), enteredId)
            } else {
                idInput.error = "Invalid ID format"
            }
        }
    }

    private fun handleSuccessfulLogin(rememberMe: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("rememberMe", rememberMe)
            putBoolean("onboarding_finished", true)
            apply()
        }
        startActivity(Intent(this, faculty::class.java))
        finish()
    }

    private fun showErrorAlert(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun validateInputs(email: EditText, pass: EditText, passwordLayout: TextInputLayout): Boolean {
        var isValid = true
        val emailText = email.text.toString().trim()
        if (emailText.isEmpty()) {
            email.error = "Email is required."
            isValid = false
        } else if (!emailText.endsWith("@phinmaed.com")) {
            email.error = "Use official @phinmaed.com email."
            isValid = false
        }
        if (pass.text.toString().isEmpty()) {
            passwordLayout.isPasswordVisibilityToggleEnabled = false
            pass.error = "Password is required."
            isValid = false
        }
        return isValid
    }
}