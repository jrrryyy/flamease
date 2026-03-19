package com.example.flamease.repository

import com.google.firebase.auth.FirebaseAuth

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) callback(true, null)
                else callback(false, it.exception?.message)
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid
}