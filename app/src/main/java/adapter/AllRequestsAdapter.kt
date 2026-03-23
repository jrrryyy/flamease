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
import java.util.Locale

class AllRequestsAdapter(private val displayList: List<RequestData>) :
    RecyclerView.Adapter<AllRequestsAdapter.ItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_request_accepted, parent, false)
        return ItemViewHolder(v)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val req = displayList[position]
        val status = req.status?.lowercase() ?: "pending"

        // --- FIX FOR DOUBLE RS NAME ---
        val building = req.building.trim().uppercase()
        val room = req.room.trim().uppercase()

        if (room.startsWith(building)) {
            // If room is "RS 101" and building is "RS", just show "RS 101"
            holder.tvRoom.text = room
        } else {
            // Otherwise show both (e.g., "MAIN 101")
            holder.tvRoom.text = "$building $room"
        }
        // ------------------------------

        req.createdAt?.let {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            holder.tvDate.text = "Today • ${sdf.format(it.toDate())}"
        }

        holder.tvStatus.text = status.uppercase()
        holder.itemView.setOnClickListener { showStatusPopup(holder.itemView.context, req) }

        holder.buttonContainer?.visibility = View.VISIBLE
        holder.btnRegistrar?.visibility = View.GONE
        holder.btnCancel?.visibility = View.VISIBLE

        when (status) {
            "accepted" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#1078B9"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_accepted)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_accepted)
                (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.accepted)
                holder.btnRegistrar?.text = "Send to GSD"
                holder.btnRegistrar?.visibility = View.VISIBLE
                holder.btnRegistrar?.setOnClickListener { showAcceptedFormPopup(holder.itemView.context, req) }
                holder.btnCancel?.setOnClickListener { showCancelDialog(holder.itemView.context, req.requestId) }
            }
            "approved" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#2ECC71"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_approved)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_approved)
                (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.approved)
                holder.buttonContainer?.visibility = View.GONE
            }
            "expired" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_expired)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_expired)
                (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.expired)
                holder.buttonContainer?.visibility = View.GONE
            }
            "rejected" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)
                (holder.iconStatus as? ImageView)?.setImageResource(R.drawable.rejected)
                holder.buttonContainer?.visibility = View.GONE
            }
            "registrar_pending", "pending" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#E67E22"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending)
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

    override fun getItemCount() = displayList.size

    class ItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvRoom: TextView = v.findViewById(R.id.title1)
        val tvDate: TextView = v.findViewById(R.id.tvDateTime)
        val tvStatus: TextView = v.findViewById(R.id.tvStatusBadge)
        val btnCancel: TextView? = v.findViewById(R.id.btnCancel)
        val btnRegistrar: TextView? = v.findViewById(R.id.btnRegistrar)
        val iconStatus: View = v.findViewById(R.id.iconBg1)
        val buttonContainer: View? = v.findViewById(R.id.buttonContainer)
    }

    private fun showStatusPopup(context: Context, request: RequestData) {
        val status = request.status?.lowercase() ?: "pending"
        val (title, message, iconRes) = when (status) {
            "approved" -> Triple("Congratulations!", "Your request for ${request.room.uppercase()} has been approved. Go to notification to view confirmation slip.", R.drawable.approved)
            "rejected" -> Triple("Request Rejected", "Sorry, your request was not accepted.", R.drawable.rejected)
            "accepted" -> Triple("Step 1 Complete!", "Accepted by Registrar. Send to GSD.", R.drawable.accepted)
            "registrar_pending" -> Triple("Almost there!", "The GSD is currently reviewing your request.", R.drawable.pending)
            "expired" -> Triple("Request has Expired", "This request has passed or was cancelled.", R.drawable.expired)
            else -> Triple("Request Pending", "Waiting for GSD approval.", R.drawable.pending)
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_status_feedback, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()
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

            dialogView.findViewById<TextView>(R.id.tvPopupBuilding)?.text = request.building.uppercase()
            dialogView.findViewById<TextView>(R.id.tvPopupRoom)?.text = request.room.uppercase()
            dialogView.findViewById<TextView>(R.id.tvPopupBlock)?.text = request.block ?: "N/A"
            dialogView.findViewById<TextView>(R.id.tvPopupDescription)?.text = request.description ?: "N/A"
            dialogView.findViewById<TextView>(R.id.tvPopupTimeSlot)?.text = request.timeSlotIndex ?: "No Time Set"

            request.createdAt?.let {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                dialogView.findViewById<TextView>(R.id.tvPopupDate)?.text = sdf.format(it.toDate())
            }

            dialogView.findViewById<Button>(R.id.btnReturn)?.setOnClickListener { alertDialog.dismiss() }
            val btnSend = dialogView.findViewById<Button>(R.id.btnSendToRegistrar)

            btnSend?.setOnClickListener {
                request.requestId?.let { id ->
                    btnSend.isEnabled = false
                    FirebaseFirestore.getInstance().collection("room_requests").document(id)
                        .update("status", "registrar_pending")
                        .addOnSuccessListener {
                            Toast.makeText(context, "Sent to GSD!", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        }
                }
            }
            alertDialog.show()
            alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        } catch (e: Exception) { Log.e("PopupError", "Error: ${e.message}") }
    }

    private fun showCancelDialog(context: Context, requestId: String?) {
        if (requestId == null) return
        AlertDialog.Builder(context)
            .setTitle("Cancel Request")
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { _, _ -> updateStatusToExpired(requestId) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun updateStatusToExpired(requestId: String) {
        FirebaseFirestore.getInstance().collection("room_requests").document(requestId)
            .update("status", "expired")
    }
}