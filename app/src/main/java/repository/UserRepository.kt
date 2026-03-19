package com.example.flamease.repository

import com.example.flamease.model.User
import com.google.firebase.database.FirebaseDatabase

class UserRepository {

    private val db = FirebaseDatabase
        .getInstance("https://flamease-c043a-default-rtdb.asia-southeast1.firebasedatabase.app")
        .getReference("Users")

    fun getUser(userId: String, callback: (User?) -> Unit) {
        db.child(userId).get().addOnSuccessListener {
            callback(it.getValue(User::class.java))
        }.addOnFailureListener {
            callback(null)
        }
    }
}