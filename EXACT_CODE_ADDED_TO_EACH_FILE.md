/**
 * EXACT CODE ADDED TO EACH ACTIVITY FILE
 * 
 * Copy this code if you need to manually add badge logic or verify what was added
 */

// ==================== FACULTY.KT ====================

// Added to class variables:
private lateinit var tvNotifBadge: TextView

// Added to onCreate():
tvNotifBadge = findViewById(R.id.tvNotifBadge)

// Added methods (after filterRequests):
private fun updateBadgeCount() {
    if (!::adapter.isInitialized) return
    val unreadCount = masterRequestList.count { it.notSeen == true }
    if (unreadCount > 0) {
        tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
        tvNotifBadge.visibility = android.view.View.VISIBLE
    } else {
        tvNotifBadge.visibility = android.view.View.GONE
    }
}

private fun markAsRead(requestId: String) {
    db.collection("room_requests").document(requestId)
        .update("notSeen", false)
        .addOnSuccessListener {
            val item = masterRequestList.find { it.requestId == requestId }
            item?.notSeen = false
            updateBadgeCount()
        }
}

// Modified in filterRequests(): Call updateBadgeCount() at the end
adapter.updateList(filteredList)
updateBadgeCount()  // ← ADD THIS LINE

// ==================== REQUEST.KT ====================

// Added to class variables:
private lateinit var tvNotifBadge: TextView

// Added to onCreate():
tvNotifBadge = findViewById(R.id.tvNotifBadge)

// Added methods (after isSameDay):
private fun updateBadgeCount() {
    val unreadCount = requestList.count { it.notSeen == true }
    if (unreadCount > 0) {
        tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
        tvNotifBadge.visibility = android.view.View.VISIBLE
    } else {
        tvNotifBadge.visibility = android.view.View.GONE
    }
}

private fun markAsRead(requestId: String) {
    db.collection("room_requests").document(requestId)
        .update("notSeen", false)
        .addOnSuccessListener {
            val item = requestList.find { it.requestId == requestId }
            item?.notSeen = false
            updateBadgeCount()
        }
}

// Modified in fetchAllUserRequests(): Add updateBadgeCount() call
requestList.sortByDescending { it.createdAt }
rvAllRequests.adapter = AllRequestsAdapter(requestList)
updateBadgeCount()  // ← ADD THIS LINE

// ==================== SETTINGS.KT ====================

// Added to class variables:
private lateinit var tvNotifBadge: TextView

// Modified in onCreate(): Add initialization
tvNotifBadge = findViewById(R.id.tvNotifBadge)  // ← ADD AFTER OTHER FINDVIEWBYID

// Added methods (before showLogoutConfirmation or at end):
private fun updateBadgeCount() {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return

    db.collection("users").document(userId).get()
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

                        if (unreadCount > 0) {
                            tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                            tvNotifBadge.visibility = android.view.View.VISIBLE
                        } else {
                            tvNotifBadge.visibility = android.view.View.GONE
                        }
                    }
            }
        }
}

override fun onResume() {
    super.onResume()
    updateBadgeCount()
}

// ==================== BUILDINGS.KT ====================

// Added to class variables:
private lateinit var tvNotifBadge: TextView

// Modified in onCreate(): Add initialization
tvNotifBadge = findViewById(R.id.tvNotifBadge)  // ← ADD WITH OTHER FINDVIEWBYID

// Added methods (after filterBuildings):
private fun updateBadgeCount() {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return

    db.collection("users").document(userId).get()
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

                        if (unreadCount > 0) {
                            tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                            tvNotifBadge.visibility = android.view.View.VISIBLE
                        } else {
                            tvNotifBadge.visibility = android.view.View.GONE
                        }
                    }
            }
        }
}

override fun onResume() {
    super.onResume()
    updateBadgeCount()
}

// ==================== ROOMS.KT ====================

// Added to imports:
import com.google.firebase.auth.FirebaseAuth

// Added to class variables:
private lateinit var tvNotifBadge: TextView

// Modified in onCreate(): Add initialization
tvNotifBadge = findViewById(R.id.tvNotifBadge)  // ← ADD AFTER setContentView

// Added methods (at end of class, before closing brace):
private fun updateBadgeCount() {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return

    db.collection("users").document(userId).get()
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

                        if (unreadCount > 0) {
                            tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                            tvNotifBadge.visibility = android.view.View.VISIBLE
                        } else {
                            tvNotifBadge.visibility = android.view.View.GONE
                        }
                    }
            }
        }
}

override fun onResume() {
    super.onResume()
    updateBadgeCount()
}

// ==================== NOTIFICATIONS.KT ====================

// Already has complete implementation:
private lateinit var tvNotifBadge: TextView

// In onCreate():
tvNotifBadge = findViewById(R.id.tvNotifBadge)

// Methods already present:
private fun updateBadgeCount() {
    if (!::adapter.isInitialized) return
    val unreadCount = adapter.getUnreadCount()
    if (unreadCount > 0) {
        tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
        tvNotifBadge.visibility = View.VISIBLE
    } else {
        tvNotifBadge.visibility = View.GONE
    }
}

// ==================== SUMMARY ====================

Changes made to each file:

faculty.kt:
  ✅ Added tvNotifBadge variable
  ✅ Added updateBadgeCount() method
  ✅ Added markAsRead() method
  ✅ Initialized tvNotifBadge in onCreate
  ✅ Called updateBadgeCount() in filterRequests()

request.kt:
  ✅ Added tvNotifBadge variable
  ✅ Added updateBadgeCount() method
  ✅ Added markAsRead() method
  ✅ Initialized tvNotifBadge in onCreate
  ✅ Called updateBadgeCount() in fetchAllUserRequests()

Settings.kt:
  ✅ Added tvNotifBadge variable
  ✅ Added updateBadgeCount() method
  ✅ Added onResume() override
  ✅ Initialized tvNotifBadge in onCreate

Buildings.kt:
  ✅ Added tvNotifBadge variable
  ✅ Added updateBadgeCount() method
  ✅ Added onResume() override
  ✅ Initialized tvNotifBadge in onCreate

Rooms.kt:
  ✅ Added FirebaseAuth import
  ✅ Added tvNotifBadge variable
  ✅ Added updateBadgeCount() method
  ✅ Added onResume() override
  ✅ Initialized tvNotifBadge in onCreate

notifications.kt:
  ✅ No changes needed (reference implementation)

