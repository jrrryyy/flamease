package com.example.flamease

import adapter.AllRequestsAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Date

class request : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvAllRequests: RecyclerView
    private val requestList = arrayListOf<RequestData>()
    private val TAG = "RequestActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_request)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        rvAllRequests = findViewById(R.id.rvAllRequests)
        rvAllRequests.layoutManager = LinearLayoutManager(this)

        fetchAllUserRequests()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // Navigation Setup
        val navClickListener = { activityClass: Class<*> ->
            val intent = Intent(this, activityClass)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.home).setOnClickListener {
            navClickListener(faculty::class.java)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            navClickListener(notifications::class.java)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.settings).setOnClickListener {
            navClickListener(Settings::class.java)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun fetchAllUserRequests() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val myIdNumber = document.getString("idNumber") ?: ""

                    // We sort manually in Kotlin to avoid requiring a Composite Index in Firestore
                    db.collection("room_requests")
                        .whereEqualTo("userId", myIdNumber)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null) return@addSnapshotListener

                            val batch = db.batch()
                            var hasExpiredUpdates = false
                            requestList.clear()

                            for (doc in snapshot.documents) {
                                val req = doc.toObject(RequestData::class.java)?.copy(requestId = doc.id)
                                if (req != null) {
                                    val requestDate = req.createdAt?.toDate()

                                    // If room is "Approved/Accepted" but date is in the past, Mark Expired
                                    if (requestDate != null && (req.status == "approved" || req.status == "accepted")) {
                                        if (isDateExpired(requestDate)) {
                                            val docRef = db.collection("room_requests").document(doc.id)
                                            batch.update(docRef, "status", "expired")
                                            hasExpiredUpdates = true

                                            requestList.add(req.copy(status = "expired"))
                                            continue
                                        }
                                    }
                                    requestList.add(req)
                                }
                            }

                            if (hasExpiredUpdates) {
                                batch.commit().addOnSuccessListener {
                                    Log.d(TAG, "Database cleaned: Old requests expired.")
                                }
                            }

                            // Manual sort by date descending
                            requestList.sortByDescending { it.createdAt }
                            rvAllRequests.adapter = AllRequestsAdapter(requestList)
                        }
                }
            }
    }

    private fun isDateExpired(requestDate: Date): Boolean {
        val calRequest = Calendar.getInstance().apply { time = requestDate }
        val calToday = Calendar.getInstance()

        // Normalize both to Midnight
        listOf(calRequest, calToday).forEach {
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        return calRequest.before(calToday)
    }

}