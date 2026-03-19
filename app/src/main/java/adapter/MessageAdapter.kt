package adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.Message
import com.example.flamease.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == 0) {
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_user, parent, false)
        } else {
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_ai, parent, false)
        }
        return MessageViewHolder(layout)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 0 else 1
    }

    @SuppressLint("NotifyDataSetChanged")
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView? = itemView.findViewById(R.id.tvTime)

        fun bind(message: Message) {
            tvMessage.text = message.text
            tvTime?.text = SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date(message.timestamp))
        }
    }
}