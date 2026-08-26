package com.example.notestaker.adapter

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.notestaker.databinding.NoteLayoutBinding
import com.example.notestaker.fragments.HomeFragmentDirections
import com.example.notestaker.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(val itemBinding: NoteLayoutBinding) : RecyclerView.ViewHolder(itemBinding.root)

    var isGhostMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val differCallback = object : DiffUtil.ItemCallback<Note>(){
        override fun areItemsTheSame(
            oldItem: Note,
            newItem: Note
        ): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.noteDesc == newItem.noteDesc &&
                    oldItem.noteTitle == newItem.noteTitle &&
                    oldItem.unlockTimestamp == newItem.unlockTimestamp
        }

        override fun areContentsTheSame(
            oldItem: Note,
            newItem: Note
        ): Boolean {
            return oldItem == newItem
        }

    }

    val differ = AsyncListDiffer(this, differCallback)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): NoteViewHolder {
        return NoteViewHolder(NoteLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(
        holder: NoteViewHolder,
        position: Int
    ) {
        val currentNote = differ.currentList[position]
        val isLocked = System.currentTimeMillis() < currentNote.unlockTimestamp

        // Clear action ribbon from previous bindings
        holder.itemBinding.actionRibbon.removeAllViews()
        holder.itemBinding.actionRibbon.visibility = View.GONE

        if (isLocked) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val unlockDate = sdf.format(Date(currentNote.unlockTimestamp))
            holder.itemBinding.noteTitle.text = "Locked Capsule 🔒"
            holder.itemBinding.noteDesc.text = "Available on: $unlockDate"
            holder.itemBinding.cardView.setCardBackgroundColor(holder.itemView.context.getColor(com.example.notestaker.R.color.white))
            holder.itemBinding.cardView.alpha = 0.5f
        } else {
            if (isGhostMode) {
                holder.itemBinding.noteTitle.text = "••••••••"
                holder.itemBinding.noteDesc.text = "••••••••••••••••••••"
                holder.itemBinding.cardView.setCardBackgroundColor(holder.itemView.context.getColor(com.example.notestaker.R.color.white))
            } else {
                holder.itemBinding.noteTitle.text = currentNote.noteTitle
                holder.itemBinding.noteDesc.text = currentNote.noteDesc

                // Mood-Reactive Styling: Change color based on keywords
                val content = "${currentNote.noteTitle} ${currentNote.noteDesc}".lowercase()
                val colorRes = when {
                    content.contains("urgent") || content.contains("must") || content.contains("deadline") -> com.example.notestaker.R.color.mood_urgent
                    content.contains("happy") || content.contains("great") || content.contains("awesome") || content.contains("love") -> com.example.notestaker.R.color.mood_joy
                    content.contains("work") || content.contains("meeting") || content.contains("todo") || content.contains("project") -> com.example.notestaker.R.color.mood_work
                    content.contains("idea") || content.contains("creative") || content.contains("think") -> com.example.notestaker.R.color.mood_idea
                    else -> com.example.notestaker.R.color.mood_neutral
                }
                holder.itemBinding.cardView.setCardBackgroundColor(holder.itemView.context.getColor(colorRes))

                // Feature #4: Smart Action Ribbon
                setupSmartActions(holder, currentNote.noteDesc)
            }
            holder.itemBinding.cardView.alpha = 1.0f
        }

        holder.itemView.setOnClickListener {
            if (isLocked) {
                Toast.makeText(it.context, "This note is locked until the set time!", Toast.LENGTH_SHORT).show()
            } else {
                val direction = HomeFragmentDirections.actionHomeFragmentToEditNoteFragment(currentNote)
                it.findNavController().navigate(direction)
            }
        }

    }

    private fun setupSmartActions(holder: NoteViewHolder, description: String) {
        val context = holder.itemView.context
        val ribbon = holder.itemBinding.actionRibbon

        // Check for Web Links
        val urlMatcher = Patterns.WEB_URL.matcher(description)
        if (urlMatcher.find()) {
            val url = urlMatcher.group()
            addActionIcon(ribbon, android.R.drawable.ic_menu_info_details) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(if (url.startsWith("http")) url else "http://$url"))
                context.startActivity(intent)
            }
        }

        // Check for Emails
        val emailMatcher = Patterns.EMAIL_ADDRESS.matcher(description)
        if (emailMatcher.find()) {
            val email = emailMatcher.group()
            addActionIcon(ribbon, android.R.drawable.ic_dialog_email) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                context.startActivity(intent)
            }
        }

        // Check for Phone Numbers
        val phoneMatcher = Patterns.PHONE.matcher(description)
        if (phoneMatcher.find()) {
            val phone = phoneMatcher.group()
            addActionIcon(ribbon, android.R.drawable.ic_menu_call) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                context.startActivity(intent)
            }
        }

        if (ribbon.childCount > 0) {
            ribbon.visibility = View.VISIBLE
        }
    }

    private fun addActionIcon(parent: LinearLayout, iconRes: Int, onClick: () -> Unit) {
        val button = ImageButton(parent.context).apply {
            setImageResource(iconRes)
            setBackgroundResource(android.R.color.transparent)
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
        parent.addView(button)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}