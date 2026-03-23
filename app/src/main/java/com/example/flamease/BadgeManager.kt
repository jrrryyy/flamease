package com.example.flamease

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

object BadgeManager {
    
    /**
     * Updates the notification badge count across any activity
     * Pass the tvNotifBadge TextView ID, and it will fetch fresh data from Firestore
     */
    fun updateBadgeCount(activity: Activity, badgeTextViewId: Int) {
        try {
            val tvNotifBadge = activity.findViewById<TextView>(badgeTextViewId) ?: return
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return
            val db = FirebaseFirestore.getInstance()

            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { userDoc ->
                    if (userDoc.exists()) {
                        val idNumber = userDoc.getString("idNumber") ?: ""
                        
                        db.collection("room_requests")
                            .whereEqualTo("userId", idNumber)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val unreadCount = snapshot.mapNotNull { doc ->
                                    doc.toObject(RequestData::class.java)
                                }.count { it.notSeen == true }
                                
                                // Update UI
                                tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                                tvNotifBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                                
                                Log.d("BadgeManager", "Badge updated: $unreadCount unread items")
                            }
                            .addOnFailureListener { e ->
                                Log.e("BadgeManager", "Failed to fetch badge count: ${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BadgeManager", "Failed to fetch user: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("BadgeManager", "Error updating badge: ${e.message}")
        }
    }
}

