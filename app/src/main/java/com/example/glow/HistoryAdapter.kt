package com.example.glow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.glow.data.CheckInEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val checkIns: List<CheckInEntity>,
    private val onItemClick: (CheckInEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemEmoji: TextView = view.findViewById(R.id.tvItemEmoji)
        val tvItemState: TextView = view.findViewById(R.id.tvItemState)
        val tvItemDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvItemResponseTime: TextView = view.findViewById(R.id.tvItemResponseTime)
        val tvItemNote: TextView = view.findViewById(R.id.tvItemNote)
        val tvItemActivities: TextView = view.findViewById(R.id.tvItemActivities)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_check_in, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val checkIn = checkIns[position]

        holder.tvItemState.text = checkIn.state
        holder.tvItemResponseTime.text = String.format("%.2fs", checkIn.responseTime)

        holder.tvItemEmoji.text = when (checkIn.state) {
            "RESTED" -> "🟢"
            "FATIGUED" -> "🟡"
            "STRESSED" -> "🔴"
            else -> "⚪"
        }

        // Show note or placeholder
        if (checkIn.note.isNotEmpty()) {
            holder.tvItemNote.text = "📝 ${checkIn.note}"
            holder.tvItemNote.setTextColor(0xFF005662.toInt())
        } else {
            holder.tvItemNote.text = "Tap to add note..."
            holder.tvItemNote.setTextColor(0xFF999999.toInt())
        }

        // Show activities if they exist
        if (checkIn.activitiesPerformed.isNotEmpty()) {
            val formattedActivities = checkIn.activitiesPerformed.replace("|", " • ")
            holder.tvItemActivities.text = "🏆 Activities: $formattedActivities"
        } else {
            holder.tvItemActivities.text = "No activities performed"
        }

        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        holder.tvItemDate.text = sdf.format(Date(checkIn.timestamp))

        holder.itemView.setOnClickListener {
            onItemClick(checkIn)
        }
    }

    override fun getItemCount() = checkIns.size
}