package adapter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.R
import com.example.flamease.RequestData
import java.text.SimpleDateFormat
import java.util.Locale

class RequestAdapter(private val requestList: MutableList<RequestData>) :
    RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvRoom: TextView = v.findViewById(R.id.tvRoomName)
        val tvDateTime: TextView = v.findViewById(R.id.tvDateTime)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val iconStatus: ImageView = v.findViewById(R.id.iconStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request_card, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requestList[position]
        val status = request.status?.lowercase() ?: "pending"

        holder.tvRoom.text = request.room.uppercase()

        val date = request.createdAt?.toDate()
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        holder.tvDateTime.text = if (date != null) sdf.format(date) else "Recently"

        holder.tvStatus.text = status.uppercase()

        // --- SYNCED VISUAL STYLES (MATCHING ALL REQUESTS ADAPTER) ---
        when (status) {
            "approved" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#2ECC71"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_approved)

                holder.iconStatus.setImageResource(R.drawable.approved)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_approved)
            }
            "accepted" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#1078B9"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_accepted)

                holder.iconStatus.setImageResource(R.drawable.accepted)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_accepted)
            }
            "rejected" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)

                holder.iconStatus.setImageResource(R.drawable.rejected)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_bagde_rejected)
            }
            "expired" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_expired)

                holder.iconStatus.setImageResource(R.drawable.expired)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_expired)
            }
            else -> { // pending or registrar_pending
                holder.tvStatus.setTextColor(Color.parseColor("#E67E22"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending)

                holder.iconStatus.setImageResource(R.drawable.pending)
                holder.iconStatus.setBackgroundResource(R.drawable.bg_badge_pending)
            }
        }

        holder.itemView.setOnClickListener {
            showStatusPopup(holder.itemView.context, request)
        }
    }

    private fun showStatusPopup(context: Context, request: RequestData) {
        val status = request.status?.lowercase() ?: "pending"

        val (title, message, iconRes) = when (status) {
            "approved" -> Triple("Congratulations!", "Your request for ${request.room.uppercase()} is approved. Go to notification to view confirmation slip.", R.drawable.approved)
            "rejected" -> Triple("Request Rejected", "Your request was not accepted.", R.drawable.rejected)
            "accepted" -> Triple("Step 1 Complete!", "Registrar department accepted. Send to GSD.", R.drawable.accepted)
            "registrar_pending" -> Triple("Processing...", "GSD is reviewing your request.", R.drawable.pending)
            "expired" -> Triple("Request Expired", "This request is no longer active.", R.drawable.expired)
            else -> Triple("Still Pending", "Your request is in the queue.", R.drawable.pending)
        }

        val builder = AlertDialog.Builder(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_status_feedback, null)
        builder.setView(dialogView)
        val alertDialog = builder.create()

        dialogView.findViewById<TextView>(R.id.tvFeedbackTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvFeedbackMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivFeedbackIcon).setImageResource(iconRes)
        dialogView.findViewById<Button>(R.id.btnStatusClose).setOnClickListener { alertDialog.dismiss() }

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.show()
    }

    override fun getItemCount() = requestList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<RequestData>) {
        requestList.clear()
        requestList.addAll(newList)
        notifyDataSetChanged()
    }
}