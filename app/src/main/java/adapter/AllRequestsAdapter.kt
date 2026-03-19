package adapter

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.R
import com.example.flamease.RequestData
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AllRequestsAdapter(private val rawList: List<RequestData>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1
    private val displayList = mutableListOf<Any>()

    init {
        groupDataByDate()
    }

    private fun groupDataByDate() {
        if (rawList.isEmpty()) return
        val sdfHeader = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val todayStr = sdfHeader.format(Date())
        var lastDate = ""

        for (item in rawList) {
            val itemDate = item.createdAt?.toDate()?.let { sdfHeader.format(it) } ?: ""
            if (itemDate != lastDate) {
                if (itemDate == todayStr) displayList.add("Today")
                else displayList.add(itemDate)
                lastDate = itemDate
            }
            displayList.add(item)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_request_accepted, parent, false)
            ItemViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayList[position]

        if (holder is HeaderViewHolder) {
            holder.tvHeader.text = item as String
        } else if (holder is ItemViewHolder) {
            val req = item as RequestData
            val status = req.status?.lowercase() ?: "pending"

            holder.tvRoom.text = "${req.building.uppercase()} ${req.room.uppercase()}"
            req.createdAt?.let {
                val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                holder.tvDate.text = sdf.format(it.toDate())
            }

            holder.tvStatus.text = status.uppercase()
            holder.itemView.setOnClickListener { showStatusPopup(holder.itemView.context, req) }

            // Default Visibility
            holder.buttonContainer?.visibility = View.VISIBLE
            holder.btnRegistrar?.visibility = View.GONE
            holder.btnCancel?.visibility = View.VISIBLE

            when (status) {
                "accepted" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#1078B9"))
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_accepted)

                    // Matching Notification style:
                    holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_accepted)
                    (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.accepted)

                    holder.btnRegistrar?.visibility = View.VISIBLE
                    holder.btnRegistrar?.setOnClickListener { showAcceptedFormPopup(holder.itemView.context, req) }
                    holder.btnCancel?.setOnClickListener { showCancelDialog(holder.itemView.context, req.requestId) }
                }
                "approved" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#2ECC71"))
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_approved)

                    // Matching Notification style:
                    holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_approved)
                    (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.approved)

                    holder.buttonContainer?.visibility = View.GONE
                }
                "expired" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_expired)

                    // Matching Notification style:
                    holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_expired)
                    (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.expired)

                    holder.buttonContainer?.visibility = View.GONE
                }
                "rejected" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)

                    // Matching Notification style:
                    holder.iconStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)
                    (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.rejected)

                    holder.buttonContainer?.visibility = View.GONE
                }
                "registrar_pending", "pending" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#E67E22"))
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending)

                    // Matching Notification style:
                    holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_pending)
                    (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.pending)

                    if (status == "pending") {
                        holder.btnCancel?.setOnClickListener { showCancelDialog(holder.itemView.context, req.requestId) }
                    } else {
                        holder.buttonContainer?.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount() = displayList.size

    // ViewHolders
    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvHeader: TextView = v.findViewById(R.id.tvDateHeader)
    }

    class ItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvRoom: TextView = v.findViewById(R.id.title1)
        val tvDate: TextView = v.findViewById(R.id.tvDateTime)
        val tvStatus: TextView = v.findViewById(R.id.tvStatusBadge)
        val btnCancel: TextView? = v.findViewById(R.id.btnCancel)
        val btnRegistrar: TextView? = v.findViewById(R.id.btnRegistrar)
        val iconStatus: View = v.findViewById(R.id.iconBg1)
        val buttonContainer: View? = v.findViewById(R.id.buttonContainer)
    }

    // --- Helper Dialog Functions (showStatusPopup, showAcceptedFormPopup, etc.) ---
    // [Keep your existing dialog functions exactly as they were in your AllRequestsAdapter]

    private fun showStatusPopup(context: Context, request: RequestData) {
        val status = request.status?.lowercase() ?: "pending"
        val (title, message, iconRes) = when (status) {
            "approved" -> Triple("Congratulations!", "Your request for ${request.room.uppercase()} has been approved. Check your notification to get your Confirmation Slip.", R.drawable.approved)
            "rejected" -> Triple("Request Rejected", "Sorry, your request was not accepted.", R.drawable.rejected)
            "accepted" -> Triple("Step 1 Complete!", "Your request has been accepted by GSD department. Send to Registrar.", R.drawable.accepted)
            "registrar_pending" -> Triple("Almost there!", "The Registrar is currently reviewing your request. Please wait for the final approval.", R.drawable.pending)
            "expired" -> Triple("Request Expired", "This request has passed or was cancelled.", R.drawable.expired)
            else -> Triple("Request Pending", "Waiting for GSD approval.", R.drawable.pending)
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_status_feedback, null)
        val builder = AlertDialog.Builder(context).setView(dialogView)
        val alertDialog = builder.create()

        dialogView.findViewById<TextView>(R.id.tvFeedbackTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvFeedbackMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivFeedbackIcon).setImageResource(iconRes)
        dialogView.findViewById<Button>(R.id.btnStatusClose).setOnClickListener { alertDialog.dismiss() }

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.show()
    }

    private fun showAcceptedFormPopup(context: Context, request: RequestData) {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_accepted, null)
            val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

            val btnSend = dialogView.findViewById<Button>(R.id.btnSendToRegistrar)
            val btnReturn = dialogView.findViewById<Button>(R.id.btnReturn)

            // Find the TextViews from your XML
            val tvDate = dialogView.findViewById<TextView>(R.id.tvPopupDate)
            val tvTimeSlot = dialogView.findViewById<TextView>(R.id.tvPopupTimeSlot)

            // Set Building, Room, Block, Description
            dialogView.findViewById<TextView>(R.id.tvPopupBuilding)?.text = request.building.uppercase()
            dialogView.findViewById<TextView>(R.id.tvPopupRoom)?.text = request.room.uppercase()
            dialogView.findViewById<TextView>(R.id.tvPopupBlock)?.text = request.block ?: "N/A"
            dialogView.findViewById<TextView>(R.id.tvPopupDescription)?.text = request.description ?: "N/A"

            // FIX: Set the Date
            request.createdAt?.let {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                tvDate?.text = sdf.format(it.toDate())
            } ?: run {
                tvDate?.text = "N/A"
            }

            // FIX: Set the Time Slot
            // If you have a list of time slots, map the index to a string here
            // For now, we will use your index or a raw string if available
            tvTimeSlot?.text = request.timeSlotIndex ?: "No Time Set"

            btnReturn?.setOnClickListener { alertDialog.dismiss() }

            btnSend?.setOnClickListener {
                request.requestId?.let { id ->
                    btnSend.isEnabled = false
                    FirebaseFirestore.getInstance().collection("room_requests").document(id)
                        .update("status", "registrar_pending")
                        .addOnSuccessListener {
                            Toast.makeText(context, "Sent to Registrar!", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        }
                }
            }

            alertDialog.show()
            alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        } catch (e: Exception) {
            Log.e("PopupError", "Error showing popup: ${e.message}")
        }
    }

    private fun showCancelDialog(context: Context, requestId: String?) {
        if (requestId == null) return
        AlertDialog.Builder(context)
            .setTitle("Cancel Request")
            .setMessage("Are you sure you want to cancel?")
            .setPositiveButton("Yes") { _, _ -> updateStatusToExpired(context, requestId) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun updateStatusToExpired(context: Context, requestId: String) {
        FirebaseFirestore.getInstance().collection("room_requests").document(requestId)
            .update("status", "expired")
    }
}