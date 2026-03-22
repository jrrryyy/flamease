package com.example.flamease

import adapter.AllRequestsAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Date

class request : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvAllRequests: RecyclerView
    private val requestList = arrayListOf<RequestData>()

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
        setupNavigation()
    }

    private fun fetchAllUserRequests() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val myIdNumber = document.getString("idNumber") ?: ""

                    db.collection("room_requests")
                        .whereEqualTo("userId", myIdNumber)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null) return@addSnapshotListener

                            requestList.clear()
                            val today = Calendar.getInstance()

                            for (doc in snapshot.documents) {
                                val req = doc.toObject(RequestData::class.java)?.copy(requestId = doc.id)
                                if (req != null && req.createdAt != null) {
                                    // Only add if date is Today
                                    if (isSameDay(req.createdAt!!.toDate(), today.time)) {
                                        requestList.add(req)
                                    }
                                }
                            }

                            requestList.sortByDescending { it.createdAt }
                            rvAllRequests.adapter = AllRequestsAdapter(requestList)
                        }
                }
            }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.home).setOnClickListener {
            startActivity(Intent(this, faculty::class.java))
        }
        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            startActivity(Intent(this, notifications::class.java))
        }
        findViewById<LinearLayout>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
    }
}