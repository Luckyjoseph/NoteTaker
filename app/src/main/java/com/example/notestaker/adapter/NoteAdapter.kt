package com.example.notestaker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.notestaker.databinding.NoteLayoutBinding
import com.example.notestaker.fragments.HomeFragmentDirections
import com.example.notestaker.model.Note

class NoteAdapter : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(val itemBinding: NoteLayoutBinding) : RecyclerView.ViewHolder(itemBinding.root)


    private val differCallback = object : DiffUtil.ItemCallback<Note>(){
        override fun areItemsTheSame(
            oldItem: Note,
            newItem: Note
        ): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.noteDesc == newItem.noteDesc &&
                    oldItem.noteTitle == newItem.noteTitle
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

        holder.itemView.setOnClickListener {
            val direction = HomeFragmentDirections.actionHomeFragmentToEditNoteFragment(currentNote)
            it.findNavController().navigate(direction)
        }

    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}