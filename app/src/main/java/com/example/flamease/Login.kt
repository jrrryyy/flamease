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

        // Look up if this email already exists in Firestore
        db.collection("users")
            .whereEqualTo("email", googleEmail)
            .get()
            .addOnSuccessListener { results ->
                if (!results.isEmpty) {
                    val existingDoc      = results.documents[0]
                    val existingUid      = existingDoc.id
                    val existingProvider = existingDoc.getString("provider") ?: "email"

                    when {
                        existingProvider == "email" -> {
                            // User registered with email+password only.
                            // Link Google to their existing Firebase Auth account
                            // so BOTH sign-in methods work under the SAME UID.
                            linkGoogleToEmailAccount(googleCredential, existingUid)
                        }
                        existingProvider.contains("google") -> {
                            // Already linked or Google-only — just sign in normally
                            auth.signInWithCredential(googleCredential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) handleSuccessfulLogin(true)
                                    else showErrorAlert("Auth Failed", task.exception?.localizedMessage ?: "Error")
                                }
                        }
                        else -> {
                            // Unknown provider state — try signing in anyway
                            auth.signInWithCredential(googleCredential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) handleSuccessfulLogin(true)
                                    else showErrorAlert("Auth Failed", task.exception?.localizedMessage ?: "Error")
                                }
                        }
                    }
                } else {
                    // No Firestore doc found — new Google user
                    auth.signInWithCredential(googleCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser ?: return@addOnCompleteListener
                                checkUserAndRedirect(googleEmail, user)
                            } else {
                                showErrorAlert("Auth Failed", task.exception?.localizedMessage ?: "Error")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                showErrorAlert("Error", e.localizedMessage ?: "Could not verify account")
            }
    }

    /**
     * THE KEY FIX: Link Google to the existing email/password account.
     *
     * How it works:
     * 1. Sign in with email/password first to get the existing Firebase user
     *    (we can't do this silently without the password, so we sign in with
     *    Google first, then use linkWithCredential on the email account)
     *
     * Actually the correct Firebase approach:
     * - Sign in with Google credential directly
     * - If Firebase throws EMAIL_ALREADY_IN_USE collision, it means the email
     *   is tied to an email/password account
     * - We then sign in with the email/password account and call
     *   linkWithCredential(googleCredential) to attach Google to it
     *
     * But since we don't have the password here, we use a simpler approach:
     * - Sign in with Google (Firebase creates a new Google-auth user)
     * - Fetch the OLD email-auth Firestore doc
     * - Copy it to the new Google UID, mark provider as "email,google"
     * - BUT ALSO keep the old doc so email login still works
     *
     * The REAL proper fix: sign in with Google, then immediately call
     * currentUser.linkWithCredential with the EMAIL credential — but that
     * requires the user's password. So instead we use fetchSignInMethodsForEmail
     * to detect the collision, then show a dialog asking for their password
     * to complete the link properly.
     */
    private fun linkGoogleToEmailAccount(
        googleCredential: com.google.firebase.auth.AuthCredential,
        existingUid: String
    ) {
        // Step 1: Try signing in with Google credential
        auth.signInWithCredential(googleCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val googleUid = auth.currentUser?.uid ?: return@addOnCompleteListener

                    if (googleUid == existingUid) {
                        // Same UID — accounts are already linked in Firebase Auth
                        // Just update the provider field in Firestore
                        db.collection("users").document(existingUid)
                            .update("provider", "email,google")
                            .addOnSuccessListener { handleSuccessfulLogin(true) }
                            .addOnFailureListener { handleSuccessfulLogin(true) } // proceed anyway
                        return@addOnCompleteListener
                    }

                    // Google sign-in succeeded but gave a DIFFERENT UID.
                    // This means Firebase Auth has two separate accounts for the same email.
                    // We need to ask the user for their password to link them properly.
                    auth.signOut() // sign out the Google session
                    showLinkAccountDialog(googleCredential, existingUid)

                } else {
                    val ex = task.exception
                    if (ex is FirebaseAuthUserCollisionException) {
                        // Google sign-in was blocked because this email belongs to
                        // an email/password account. Ask for password to link.
                        auth.signOut()
                        showLinkAccountDialog(googleCredential, existingUid)
                    } else {
                        showErrorAlert("Sign-In Failed", ex?.localizedMessage ?: "Error")
                    }
                }
            }
    }

    /**
     * Shows a password dialog to complete account linking.
     * The user enters their email/password to re-authenticate,
     * then we call linkWithCredential(googleCredential) to attach
     * Google to their existing account under the SAME UID.
     * After this, BOTH email+password AND Google sign-in work.
     */
    private fun showLinkAccountDialog(
        googleCredential: com.google.firebase.auth.AuthCredential,
        existingUid: String
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val tvMessage = TextView(this).apply {
            text = "You already have an account with this email.\nEnter your password to link Google sign-in so you can use both."
            setPadding(0, 0, 0, 16)
        }
        val etPassword = EditText(this).apply {
            hint = "Your current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(tvMessage)
        layout.addView(etPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Link Google Account")
            .setView(layout)
            .setPositiveButton("Link") { _, _ -> }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = etPassword.text.toString().trim()
            if (password.isEmpty()) {
                etPassword.error = "Password required"
                return@setOnClickListener
            }

            // Get the email for this UID from Firestore
            db.collection("users").document(existingUid).get()
                .addOnSuccessListener { doc ->
                    val email = doc.getString("email") ?: return@addOnSuccessListener

                    // Sign in with email/password to get the existing Firebase user
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { signInTask ->
                            if (!signInTask.isSuccessful) {
                                etPassword.error = "Incorrect password"
                                return@addOnCompleteListener
                            }

                            // Now link Google credential to this email/password account
                            // This attaches Google sign-in to the SAME UID — no migration!
                            auth.currentUser?.linkWithCredential(googleCredential)
                                ?.addOnCompleteListener { linkTask ->
                                    if (linkTask.isSuccessful) {
                                        // Update Firestore provider field
                                        db.collection("users").document(existingUid)
                                            .update("provider", "email,google")
                                            .addOnSuccessListener {
                                                dialog.dismiss()
                                                Log.d(TAG, "Successfully linked Google to email account $existingUid")
                                                handleSuccessfulLogin(true)
                                            }
                                    } else {
                                        Log.e(TAG, "Link failed: ${linkTask.exception?.message}")
                                        showErrorAlert("Link Failed", linkTask.exception?.localizedMessage ?: "Could not link accounts")
                                    }
                                }
                        }
                }
        }
    }

    private fun checkUserAndRedirect(email: String, user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentProvider = document.getString("provider") ?: ""
                    if (!currentProvider.contains("google")) {
                        db.collection("users").document(user.uid)
                            .update("provider", if (currentProvider.isEmpty()) "google" else "$currentProvider,google")
                    }
                    handleSuccessfulLogin(true)
                } else {
                    // Brand new Google user
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
                            .addOnSuccessListener { handleSuccessfulLogin(true) }
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
