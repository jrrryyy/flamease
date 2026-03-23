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
            Log.d(TAG, "=== onActivityResult called with resultCode: $resultCode (RESULT_OK=$RESULT_OK, RESULT_CANCELED=${RESULT_CANCELED})")
            try {
                // Try to get the signed-in account regardless of result code
                // The actual error will be in the task
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                Log.d(TAG, "=== Got GoogleSignIn task")
                
                try {
                    val account = task.getResult(ApiException::class.java)
                    Log.d(TAG, "=== Successfully got account from task")
                    
                    if (account == null) {
                        Log.e(TAG, "=== Google account is null")
                        Toast.makeText(this, "Unable to retrieve account information. Please try again.", Toast.LENGTH_SHORT).show()
                        showErrorAlert("Error", "Could not get Google account information. Please try again.")
                        return
                    }

                    val email = account.email
                    Log.d(TAG, "=== Google Sign-In successful. Email: $email")

                    if (email != null && email.endsWith("@phinmaed.com")) {
                        if (account.idToken == null) {
                            Log.e(TAG, "=== Google ID token is null")
                            Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show()
                            showErrorAlert("Error", "Google authentication failed. Please try again.")
                            return
                        }
                        Log.d(TAG, "=== Proceeding with Firebase authentication for: $email")
                        Toast.makeText(this, "Signing in with Google...", Toast.LENGTH_SHORT).show()
                        firebaseAuthWithGoogle(account.idToken!!, email)
                    } else {
                        googleSignInClient.signOut()
                        Toast.makeText(this, "Please use your official @phinmaed.com email", Toast.LENGTH_LONG).show()
                        showErrorAlert("Access Restricted", "Please use your official @phinmaed.com email.\nProvided: $email")
                        Log.w(TAG, "User tried to sign in with non-phinmaed email: $email")
                    }
                } catch (e: ApiException) {
                    Log.e(TAG, "=== ApiException caught. Status code: ${e.statusCode}, Message: ${e.message}")
                    when (e.statusCode) {
                        12501 -> {
                            Log.d(TAG, "=== User canceled sign-in (12501)")
                            Toast.makeText(this, "Sign-in was canceled", Toast.LENGTH_SHORT).show()
                            showErrorAlert("Canceled", "Google Sign-In was canceled. Please try again.")
                        }
                        12500 -> {
                            Log.e(TAG, "=== Network error (12500)")
                            Toast.makeText(this, "Network connection failed. Please check your internet.", Toast.LENGTH_LONG).show()
                            showErrorAlert("Network Error", "Unable to connect. Please check your internet connection and try again.")
                        }
                        else -> {
                            Log.e(TAG, "=== Other error: ${e.statusCode}")
                            Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                            showErrorAlert("Sign-In Error", "Google Sign-In failed. Please try again.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== Unexpected exception in onActivityResult: ${e.message}", e)
                Toast.makeText(this, "An error occurred. Please try again.", Toast.LENGTH_LONG).show()
                showErrorAlert("Error", "An unexpected error occurred. Please try again.")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, googleEmail: String) {
        Log.d(TAG, "=== STEP 1: Starting Firebase authentication with Google for email: $googleEmail")
        val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
        Log.d(TAG, "=== STEP 2: Google credential created successfully")

        // ✅ FIX: Use fetchSignInMethodsForEmail to detect provider collision BEFORE auth
        auth.fetchSignInMethodsForEmail(googleEmail)
            .addOnCompleteListener { task ->
                Log.d(TAG, "=== STEP 3: fetchSignInMethodsForEmail completed")
                if (!task.isSuccessful) {
                    Log.e(TAG, "=== ERROR at STEP 3: fetchSignInMethodsForEmail failed: ${task.exception?.message}")
                    runOnUiThread {
                        Toast.makeText(this@Login, "Could not verify email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        showErrorAlert("Verification Failed", "Could not verify email: ${task.exception?.message}")
                    }
                    return@addOnCompleteListener
                }

                val signInMethods = task.result?.signInMethods ?: emptyList()
                Log.d(TAG, "=== STEP 4: Sign-in methods for $googleEmail: $signInMethods")

                // Check what provider(s) exist for this email in Firebase Auth
                val hasEmailPassword = signInMethods.contains("password")
                val hasGoogle = signInMethods.contains("google.com")
                
                Log.d(TAG, "=== STEP 5: Provider analysis - hasEmailPassword: $hasEmailPassword, hasGoogle: $hasGoogle")

                when {
                    // Case 1: Email exists, Google doesn't - need to link
                    hasEmailPassword && !hasGoogle -> {
                        Log.d(TAG, "=== CASE 1: Email exists, Google doesn't. Showing link dialog.")
                        runOnUiThread {
                            showLinkAccountDialog(googleCredential, googleEmail)
                        }
                    }

                    // Case 2: Google already exists (no email) - just sign in
                    hasGoogle && !hasEmailPassword -> {
                        Log.d(TAG, "=== CASE 2: Only Google exists. Attempting to sign in...")
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                Log.d(TAG, "=== CASE 2 STEP 6: signInWithCredential completed")
                                if (signInTask.isSuccessful) {
                                    Log.d(TAG, "=== CASE 2 SUCCESS: Signed in with Google")
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    Log.d(TAG, "=== CASE 2 STEP 7: Got current user, UID: ${user.uid}")
                                    updateProviderIfNeeded(user.uid, "google")
                                    handleSuccessfulLogin(true)
                                }
                                else {
                                    val errorMsg = signInTask.exception?.message ?: "Unknown error"
                                    Log.e(TAG, "=== CASE 2 FAILED at STEP 6: $errorMsg")
                                    Log.e(TAG, "=== Exception type: ${signInTask.exception?.javaClass?.simpleName}")
                                    runOnUiThread {
                                        Toast.makeText(this@Login, "Case 2 Login failed: $errorMsg", Toast.LENGTH_LONG).show()
                                        showErrorAlert("Auth Failed (Case 2)", errorMsg)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "=== CASE 2 FAILURE LISTENER: ${e.message}")
                                Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
                                runOnUiThread {
                                    Toast.makeText(this@Login, "Case 2 Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showErrorAlert("Error (Case 2)", e.message ?: "Unknown error")
                                }
                            }
                    }

                    // Case 3: Both exist - already linked, just sign in
                    hasEmailPassword && hasGoogle -> {
                        Log.d(TAG, "=== CASE 3: Both email and Google exist. Attempting to sign in...")
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                Log.d(TAG, "=== CASE 3 STEP 6: signInWithCredential completed")
                                if (signInTask.isSuccessful) {
                                    Log.d(TAG, "=== CASE 3 SUCCESS: Signed in with Google")
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    Log.d(TAG, "=== CASE 3 STEP 7: Got current user, UID: ${user.uid}")
                                    updateProviderIfNeeded(user.uid, "email,google")
                                    handleSuccessfulLogin(true)
                                }
                                else {
                                    val errorMsg = signInTask.exception?.message ?: "Unknown error"
                                    Log.e(TAG, "=== CASE 3 FAILED at STEP 6: $errorMsg")
                                    Log.e(TAG, "=== Exception type: ${signInTask.exception?.javaClass?.simpleName}")
                                    runOnUiThread {
                                        Toast.makeText(this@Login, "Case 3 Login failed: $errorMsg", Toast.LENGTH_LONG).show()
                                        showErrorAlert("Auth Failed (Case 3)", errorMsg)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "=== CASE 3 FAILURE LISTENER: ${e.message}")
                                Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
                                runOnUiThread {
                                    Toast.makeText(this@Login, "Case 3 Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showErrorAlert("Error (Case 3)", e.message ?: "Unknown error")
                                }
                            }
                    }

                    // Case 4: Neither exist - new Google user
                    else -> {
                        Log.d(TAG, "=== CASE 4: New Google user. Attempting to sign in...")
                        auth.signInWithCredential(googleCredential)
                            .addOnCompleteListener { signInTask ->
                                Log.d(TAG, "=== CASE 4 STEP 6: signInWithCredential completed")
                                if (signInTask.isSuccessful) {
                                    Log.d(TAG, "=== CASE 4 SUCCESS: Signed in with Google")
                                    val user = auth.currentUser ?: return@addOnCompleteListener
                                    Log.d(TAG, "=== CASE 4 STEP 7: Got current user, UID: ${user.uid}")
                                    checkUserAndRedirect(googleEmail, user)
                                } else {
                                    val errorMsg = signInTask.exception?.message ?: "Unknown error"
                                    Log.e(TAG, "=== CASE 4 FAILED at STEP 6: $errorMsg")
                                    Log.e(TAG, "=== Exception type: ${signInTask.exception?.javaClass?.simpleName}")
                                    runOnUiThread {
                                        Toast.makeText(this@Login, "Case 4 Login failed: $errorMsg", Toast.LENGTH_LONG).show()
                                        showErrorAlert("Auth Failed (Case 4)", errorMsg)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "=== CASE 4 FAILURE LISTENER: ${e.message}")
                                Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
                                runOnUiThread {
                                    Toast.makeText(this@Login, "Case 4 Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showErrorAlert("Error (Case 4)", e.message ?: "Unknown error")
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "=== ERROR at STEP 3 (Failure Listener): fetchSignInMethodsForEmail error: ${e.message}")
                Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
                runOnUiThread {
                    Toast.makeText(this@Login, "Verification error: ${e.message}", Toast.LENGTH_LONG).show()
                    showErrorAlert("Verification Error", "Failed to check authentication methods: ${e.message}")
                }
            }
    }

    /**
     * ✅ Helper: Updates Firestore provider field if needed
     * IMPORTANT: MERGES providers instead of replacing to preserve all auth methods
     */
    private fun updateProviderIfNeeded(uid: String, newProvider: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val currentProvider = doc.getString("provider") ?: ""
                    
                    // MERGE providers instead of replace
                    // Split by comma, combine sets, sort, and rejoin
                    val currentSet = if (currentProvider.isNotEmpty()) currentProvider.split(",").toSet() else emptySet()
                    val newSet = newProvider.split(",").toSet()
                    val mergedProviders = (currentSet + newSet).sorted().joinToString(",")
                    
                    // Only update if actually changed
                    if (currentProvider != mergedProviders) {
                        Log.d(TAG, "Updating provider from '$currentProvider' to '$mergedProviders'")
                        db.collection("users").document(uid)
                            .update("provider", mergedProviders)
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update provider: ${e.message}")
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to read user document: ${e.message}")
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
                    // Ensure provider tracking is accurate by MERGING, not replacing
                    if (!currentProvider.contains("google")) {
                        // Use updateProviderIfNeeded to properly merge
                        updateProviderIfNeeded(user.uid, "google")
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
                                Log.d(TAG, "New Google user created successfully")
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
