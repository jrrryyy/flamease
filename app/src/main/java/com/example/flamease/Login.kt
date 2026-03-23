package com.example.flamease

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
        val etEmail        = findViewById<EditText>(R.id.etEmail)
        val etPassword     = findViewById<EditText>(R.id.etPassword)
        val btnLogin       = findViewById<Button>(R.id.btnLogin)
        val createAccount  = findViewById<TextView>(R.id.txtCreateacc)
        val forgetPassword = findViewById<TextView>(R.id.txtForgetPassword)
        val googleBtn      = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogleSignIn)

        for (i in 0 until googleBtn.childCount) {
            val v = googleBtn.getChildAt(i)
            if (v is TextView) { v.text = "Sign in with Google"; break }
        }

        googleBtn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, REQ_ONE_TAP)
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

        createAccount.setOnClickListener { startActivity(Intent(this, createaccount::class.java)) }
        forgetPassword.setOnClickListener { startActivity(Intent(this, forget_password::class.java)) }

        sharedPreferences = getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
        val cbRememberMe = findViewById<CheckBox>(R.id.checkBox)

        if (sharedPreferences.getBoolean("rememberMe", false) && auth.currentUser != null) {
            handleSuccessfulLogin(true)
        }

        btnLogin.setOnClickListener {
            if (validateInputs(etEmail, etPassword, passwordLayout)) {
                val email    = etEmail.text.toString().trim()
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
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val email = account?.email
                if (email != null && email.endsWith("@phinmaed.com")) {
                    firebaseAuthWithGoogle(account.idToken!!, email)
                } else {
                    googleSignInClient.signOut()
                    showErrorAlert("Access Restricted", "Please use your official @phinmaed.com email.")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: ${e.statusCode}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, googleEmail: String) {
        val googleCredential = GoogleAuthProvider.getCredential(idToken, null)

        // ✅ FIX: Use fetchSignInMethodsForEmail to detect provider collision BEFORE auth
        auth.fetchSignInMethodsForEmail(googleEmail)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    showErrorAlert("Error", "Could not verify email: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val signInMethods = task.result?.signInMethods ?: emptyList()
                Log.d(TAG, "Sign-in methods for $googleEmail: $signInMethods")

                // Check what provider(s) exist for this email in Firebase Auth
                val hasEmailPassword = signInMethods.contains("password")
                val hasGoogle = signInMethods.contains("google.com")

                when {
                    // Case 1: Email exists, Google doesn't - need to link
                    hasEmailPassword && !hasGoogle -> {
                        showLinkAccountDialog(googleCredential, googleEmail)
                    }

                    // Case 2: Google already exists (no email) - just sign in
                    hasGoogle && !hasEmailPassword -> {
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                if (signInTask.isSuccessful) {
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    // Update Firestore to mark Google provider
                                    updateProviderIfNeeded(user.uid, "google")
                                    handleSuccessfulLogin(true)
                                }
                                else showErrorAlert("Auth Failed", signInTask.exception?.localizedMessage ?: "Error")
                            }
                    }

                    // Case 3: Both exist - already linked, just sign in
                    hasEmailPassword && hasGoogle -> {
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                if (signInTask.isSuccessful) {
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    // Ensure provider is marked as both
                                    updateProviderIfNeeded(user.uid, "email,google")
                                    handleSuccessfulLogin(true)
                                }
                                else showErrorAlert("Auth Failed", signInTask.exception?.localizedMessage ?: "Error")
                            }
                    }

                    // Case 4: Neither exist - new Google user
                    else -> {
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                if (signInTask.isSuccessful) {
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    checkUserAndRedirect(googleEmail, user)
                                } else {
                                    showErrorAlert("Auth Failed", signInTask.exception?.localizedMessage ?: "Error")
                                }
                            }
                    }
                }
            }
    }

    /**
     * ✅ Helper: Updates Firestore provider field if needed
     */
    private fun updateProviderIfNeeded(uid: String, newProvider: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val currentProvider = doc.getString("provider") ?: ""
                    if (currentProvider != newProvider) {
                        db.collection("users").document(uid)
                            .update("provider", newProvider)
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Failed to update provider: ${e.message}")
                            }
                    }
                }
            }
    }

    /**
     * ✅ IMPROVED: Shows a password dialog to link Google to existing email account.
     * This uses the proper Firebase method: linkWithCredential
     */
    private fun showLinkAccountDialog(
        googleCredential: com.google.firebase.auth.AuthCredential,
        googleEmail: String
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val tvMessage = TextView(this).apply {
            text = "This email already has an account with password.\n\nEnter your password to link Google sign-in with your existing account. After this, you can use BOTH methods to log in."
            setPadding(0, 0, 0, 16)
        }
        val etPassword = EditText(this).apply {
            hint = "Your account password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(tvMessage)
        layout.addView(etPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Link Google Account")
            .setView(layout)
            .setPositiveButton("Link", null)  // Set to null, we'll override below
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.show()

        // Override positive button to add validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = etPassword.text.toString().trim()
            if (password.isEmpty()) {
                etPassword.error = "Password required"
                return@setOnClickListener
            }

            // ✅ Step 1: Sign in with email/password to verify user identity
            auth.signInWithEmailAndPassword(googleEmail, password)
                .addOnCompleteListener { signInTask ->
                    if (!signInTask.isSuccessful) {
                        etPassword.error = "Incorrect password"
                        Log.e(TAG, "Email sign-in failed: ${signInTask.exception?.message}")
                        return@addOnCompleteListener
                    }

                    val currentUser = auth.currentUser ?: return@addOnCompleteListener
                    Log.d(TAG, "Email sign-in successful, UID: ${currentUser.uid}")

                    // ✅ Step 2: Link Google credential to this existing email account
                    // IMPORTANT: This preserves the password method while adding Google
                    currentUser.linkWithCredential(googleCredential)
                        .addOnCompleteListener { linkTask ->
                            if (linkTask.isSuccessful) {
                                Log.d(TAG, "Successfully linked Google to email account")

                                // ✅ Step 3: Update Firestore provider field to show both methods
                                // Password is ALWAYS preserved in Firebase Auth - no action needed
                                db.collection("users").document(currentUser.uid)
                                    .update("provider", "email,google")
                                    .addOnSuccessListener {
                                        dialog.dismiss()
                                        Toast.makeText(this, "Account linked successfully! You can now use both email/password and Google to sign in.", Toast.LENGTH_SHORT).show()
                                        handleSuccessfulLogin(true)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to update provider: ${e.message}")
                                        dialog.dismiss()
                                        // Still proceed even if Firestore update fails - auth linkage is what matters
                                        handleSuccessfulLogin(true)
                                    }
                            } else {
                                Log.e(TAG, "Link failed: ${linkTask.exception?.message}")
                                // Common error: email-already-in-use means Google credential is already linked to another account
                                if (linkTask.exception?.message?.contains("email-already-in-use", ignoreCase = true) == true) {
                                    showErrorAlert("Link Failed", "This Google account is already linked to another email address.")
                                } else {
                                    showErrorAlert("Link Failed", linkTask.exception?.localizedMessage ?: "Could not link Google account")
                                }
                            }
                        }
                }
                .addOnFailureListener { e ->
                    etPassword.error = "Sign-in failed: ${e.message}"
                    Log.e(TAG, "Email sign-in error: ${e.message}")
                }
        }
    }

    private fun checkUserAndRedirect(email: String, user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentProvider = document.getString("provider") ?: ""
                    // Ensure provider tracking is accurate
                    if (!currentProvider.contains("google")) {
                        db.collection("users").document(user.uid)
                            .update("provider", if (currentProvider.isEmpty()) "google" else "$currentProvider,google")
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Failed to update provider: ${e.message}")
                            }
                    }
                    handleSuccessfulLogin(true)
                } else {
                    // Brand new Google user - show registration details dialog
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
                            "provider"  to "google"
                        )
                        db.collection("users").document(user.uid).set(newUser)
                            .addOnSuccessListener { 
                                handleSuccessfulLogin(true)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to create user document: ${e.message}")
                                showErrorAlert("Error", "Could not create account. Please try again.")
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check user: ${e.message}")
                showErrorAlert("Error", "Could not verify account. Please try again.")
            }
    }

    private fun showRegistrationDetailsDialog(onDetailsEntered: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Complete Your Profile").setCancelable(false)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val roles   = arrayOf("Student", "Instructor")
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
            val enteredId  = idInput.text.toString().trim()
            val idPattern  = Regex("^(\\d{2}-\\d{4}-\\d{6})|(\\d{2}-\\d{2}-\\d{4}-\\d{6})$")
            if (enteredId.matches(idPattern)) {
                dialog.dismiss()
                onDetailsEntered(spinner.selectedItem.toString(), enteredId)
            } else {
                idInput.error = "Invalid ID format"
            }
        }
    }

    private fun handleSuccessfulLogin(rememberMe: Boolean) {
        val currentUser = auth.currentUser ?: return
        val userId      = currentUser.uid
        val userEmail   = currentUser.email ?: ""

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    proceedWithLogin(document, rememberMe)
                } else {
                    // Fallback: search by email in case UID doesn't match
                    Log.w(TAG, "No doc for UID $userId — searching by email")
                    db.collection("users")
                        .whereEqualTo("email", userEmail)
                        .get()
                        .addOnSuccessListener { results ->
                            if (!results.isEmpty) {
                                proceedWithLogin(results.documents[0], rememberMe)
                            } else {
                                Log.e(TAG, "No user found for email $userEmail")
                                Toast.makeText(this, "Account not found. Please register.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Email fallback failed: ${e.message}")
                            Toast.makeText(this, "Login error. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Login fetch failed: ${e.message}")
                Toast.makeText(this, "Login error. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun proceedWithLogin(document: com.google.firebase.firestore.DocumentSnapshot, rememberMe: Boolean) {
        if (document.getString("status") == "suspended") {
            sharedPreferences.edit().clear().apply()
            auth.signOut()
            Toast.makeText(this, "Your account is suspended.", Toast.LENGTH_LONG).show()
            return
        }
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
        if (emailText.isEmpty()) { email.error = "Email is required."; isValid = false }
        else if (!emailText.endsWith("@phinmaed.com")) { email.error = "Use official @phinmaed.com email."; isValid = false }
        if (pass.text.toString().isEmpty()) {
            passwordLayout.isPasswordVisibilityToggleEnabled = false
            pass.error = "Password is required."
            isValid = false
        }
        return isValid
    }
}
