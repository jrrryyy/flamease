package com.example.flamease

import adapter.NotificationAdapter
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class notifications : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvNotif: RecyclerView
    private val masterNotifList = arrayListOf<RequestData>()
    private lateinit var adapter: NotificationAdapter
    private lateinit var btnDelete: ImageView
    private lateinit var btnSelectAll: TextView
    private lateinit var selectionBar: RelativeLayout
    private lateinit var deletedManager: DeletedNotificationsManager
    private var deletedIds: Set<String> = emptySet()
    private lateinit var tvNotifBadge: TextView
    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)

        deletedManager = DeletedNotificationsManager(this)
        rvNotif = findViewById(R.id.rvNotifications)
        btnDelete = findViewById(R.id.btnDeleteNotif)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        selectionBar = findViewById(R.id.selectionBar)
        tvNotifBadge = findViewById(R.id.tvNotifBadge)

        rvNotif.layoutManager = LinearLayoutManager(this)

        fetchData()
        setupButtons()
    }

    // ✅ Safely dismiss dialog when activity stops (prevents WindowLeaked)
    override fun onStop() {
        super.onStop()
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        btnSelectAll.setOnClickListener { adapter.selectAll() }

        btnDelete.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isNotEmpty()) {
                showSafeDialog(
                    AlertDialog.Builder(this)
                        .setTitle("Remove Items")
                        .setMessage("Remove ${ids.size} items from your view?")
                        .setPositiveButton("Remove") { _, _ -> performLocalDelete(ids) }
                        .setNegativeButton("Cancel", null)
                        .create()
                )
            }
        }

        findViewById<LinearLayout>(R.id.home).setOnClickListener {
            finish()
        }
        findViewById<LinearLayout>(R.id.request).setOnClickListener {
            val intent = Intent(this, request::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
        findViewById<LinearLayout>(R.id.settings).setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    // ✅ Central safe dialog launcher — guards against finishing activity
    private fun showSafeDialog(dialog: AlertDialog) {
        if (isFinishing || isDestroyed) return
        activeDialog?.dismiss()
        activeDialog = dialog
        dialog.setOnDismissListener { activeDialog = null }
        dialog.show()
    }

    private fun fetchData() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch {
            deletedIds = deletedManager.deletedIdsFlow.first()
            db.collection("users").document(firebaseUser.uid).get()
                .addOnSuccessListener { userDoc ->
                    val idNumber = userDoc.getString("idNumber") ?: ""
                    db.collection("room_requests")
                        .whereEqualTo("userId", idNumber)
                        .addSnapshotListener { snapshots, _ ->
                            if (snapshots == null) return@addSnapshotListener
                            masterNotifList.clear()
                            for (doc in snapshots.documents) {
                                val req = doc.toObject(RequestData::class.java)
                                if (req != null) {
                                    req.requestId = doc.id
                                    masterNotifList.add(req)
                                }
                            }
                            updateUI()
                        }
                }
        }
    }

    private fun updateUI() {
        val filtered = masterNotifList
            .filter { it.requestId !in deletedIds }
            .sortedByDescending { it.createdAt }

        if (!::adapter.isInitialized) {
            adapter = NotificationAdapter(
                filtered,
                { isMode ->
                    selectionBar.visibility = if (isMode) View.VISIBLE else View.GONE
                    btnDelete.visibility = if (isMode) View.VISIBLE else View.GONE
                },
                { clickedItem ->
                    // ✅ onItemClick: only show confirmation slip for approved
                    if (clickedItem.status.lowercase() == "approved") {
                        showConfirmationSlip(clickedItem)
                    }
                },
                { feedbackItem ->
                    showFeedbackDialog(feedbackItem)
                },
                { requestId ->
                    markAsRead(requestId)
                },
                // ✅ New callback: status popup handled in activity, not adapter
                { requestData ->
                    showStatusPopup(requestData)
                }
            )
            rvNotif.adapter = adapter
        } else {
            adapter.updateData(filtered)
        }

        updateBadgeCount()
    }

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

    private fun markAsRead(requestId: String) {
        db.collection("room_requests").document(requestId)
            .update("notSeen", false)
            .addOnSuccessListener {
                val item = masterNotifList.find { it.requestId == requestId }
                item?.notSeen = false
                updateBadgeCount()
            }
    }

    // ✅ Moved from adapter to activity — safe dialog context
    private fun showStatusPopup(request: RequestData) {
        val status = request.status.lowercase()
        val bldg = request.building.uppercase()
        val rm = request.room.uppercase()
        val loc = if (rm.startsWith(bldg)) rm else "$bldg $rm"

        val (title, message) = when (status) {
            "rejected" -> Pair("Request Rejected", "Your request for $loc was not accepted.")
            "accepted" -> Pair("Step 1 Complete!", "Accepted by GSD. Now send to Registrar.")
            "registrar_pending" -> Pair("Almost there!", "The Registrar is currently reviewing your request.")
            "expired" -> Pair("Request Expired", "This request for $loc has passed its time limit.")
            else -> Pair("Request Pending", "Waiting for approval.")
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_status_feedback, null)
        val alertDialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvFeedbackTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvFeedbackMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivFeedbackIcon)?.visibility = View.GONE
        dialogView.findViewById<Button>(R.id.btnStatusClose).setOnClickListener {
            alertDialog.dismiss()
        }

        showSafeDialog(alertDialog)
    }

    private fun showConfirmationSlip(request: RequestData) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmation_slip, null)
        val alertDialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvConfirmationPermit).text = request.permit ?: "N/A"

        val building = request.building.uppercase()
        val room = request.room.uppercase()
        val loc = if (room.startsWith(building)) room else "$building $room"

        dialogView.findViewById<TextView>(R.id.tvConfirmationMessage).text =
            "Request Approved. Please present this confirmation slip to the authorized personnel at $loc."

        dialogView.findViewById<Button>(R.id.btnCloseSlip).setOnClickListener {
            alertDialog.dismiss()
        }

        showSafeDialog(alertDialog)
    }

    private fun showFeedbackDialog(request: RequestData) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_feedback, null)
        val alertDialog = AlertDialog.Builder(this).setView(dialogView).create()

        val etHeader = dialogView.findViewById<EditText>(R.id.etFeedbackHeader)
        val etBody = dialogView.findViewById<EditText>(R.id.etFeedbackBody)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitFeedback)

        etHeader.text?.clear()
        etBody.text?.clear()

        btnSubmit.setOnClickListener {
            val subject = etHeader.text.toString().trim()
            val message = etBody.text.toString().trim()

            if (subject.isNotEmpty() && message.isNotEmpty()) {
                val feedbackData = hashMapOf(
                    "requestId" to request.requestId,
                    "subject" to subject,
                    "message" to message,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "userId" to request.userId
                )
                db.collection("feedbacks").add(feedbackData).addOnSuccessListener {
                    Toast.makeText(this, "Feedback submitted!", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        showSafeDialog(alertDialog)
    }

    private fun performLocalDelete(ids: List<String>) {
        lifecycleScope.launch {
            deletedManager.markAsDeleted(ids)
            deletedIds = deletedIds + ids
            updateUI()
            adapter.clearSelection()
        }
    }
}