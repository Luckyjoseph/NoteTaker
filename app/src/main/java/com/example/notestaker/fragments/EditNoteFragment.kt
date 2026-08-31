package com.example.notestaker.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.example.notestaker.MainActivity
import com.example.notestaker.R
import com.example.notestaker.databinding.FragmentEditNoteBinding
import com.example.notestaker.model.Note
import com.example.notestaker.utils.GeminiHelper
import com.example.notestaker.viewmodel.NoteViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Calendar

class EditNoteFragment : Fragment(R.layout.fragment_edit_note), MenuProvider {

    private var editNoteBinding: FragmentEditNoteBinding? = null
    private val binding get() = editNoteBinding!!

    private lateinit var noteViewModel: NoteViewModel
    private lateinit var currentNote: Note

    private val args: EditNoteFragmentArgs by navArgs()

    private var selectedUnlockTimestamp: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        editNoteBinding = FragmentEditNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        noteViewModel = (activity as MainActivity).noteViewModel
        currentNote = args.note!!

        selectedUnlockTimestamp = currentNote.unlockTimestamp

        binding.editNoteTitle.setText(currentNote.noteTitle)
        binding.editNoteDesc.setText(currentNote.noteDesc)

        binding.editNoteFab.setOnClickListener {

            val noteTitle = binding.editNoteTitle.text.toString().trim()
            val noteDesc = binding.editNoteDesc.text.toString().trim()

            if (noteTitle.isNotEmpty()){

                val note = Note (currentNote.id, noteTitle, noteDesc, selectedUnlockTimestamp)
                noteViewModel.updateNote(note)
                view.findNavController().popBackStack(R.id.homeFragment, false)

            } else {
                Toast.makeText(context, "Please enter note title" , Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteNote(){
        AlertDialog.Builder(requireActivity()).apply {
            setTitle("Delete Note")
            setMessage("Do you want to delete this note?")
            setPositiveButton("Delete"){_,_ ->
                noteViewModel.deleteNote(currentNote)
                Toast.makeText(context, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                view?.findNavController()?.popBackStack(R.id.homeFragment, false)
            }
            setNegativeButton("Cancel", null)
        }.create().show()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        if (selectedUnlockTimestamp > 0) {
            calendar.timeInMillis = selectedUnlockTimestamp
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker(calendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(calendar: Calendar) {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                selectedUnlockTimestamp = calendar.timeInMillis
                Toast.makeText(context, "Note will be locked until: ${calendar.time}", Toast.LENGTH_LONG).show()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menu.clear()
        menuInflater.inflate(R.menu.menu_edit_note, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when(menuItem.itemId){

            R.id.deleteMenu -> {
                deleteNote()
                true
            }
            R.id.lockMenu -> {
                showDatePicker()
                true
            }
            R.id.summarizeMenu -> {
                summarizeNote()
                true
            }
            else -> false
        }

    }

    private fun summarizeNote() {
        val noteDesc = binding.editNoteDesc.text.toString().trim()
        if (noteDesc.isNotEmpty()) {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)

            val dialog = BottomSheetDialog(requireContext())
            val view = layoutInflater.inflate(R.layout.bottom_sheet_summary, null)
            dialog.setContentView(view)

            val summaryText = view.findViewById<TextView>(R.id.summaryText)
            val progressBar = view.findViewById<View>(R.id.summaryProgressBar)
            val scrollView = view.findViewById<View>(R.id.summaryScrollView)
            val copyButton = view.findViewById<Button>(R.id.copyButton)
            val shareButton = view.findViewById<Button>(R.id.shareButton)


            progressBar.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            copyButton.isEnabled = false
            shareButton.isEnabled = false

            dialog.show()

            lifecycleScope.launch {
                val summary = GeminiHelper.summarize(noteDesc)
                progressBar.visibility = View.GONE
                scrollView.visibility = View.VISIBLE

                if (summary != null) {
                    summaryText.text = formatSummaryText(summary)
                    copyButton.isEnabled = true
                    shareButton.isEnabled = true

                    copyButton.setOnClickListener {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI Summary", summary)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                    }

                    shareButton.setOnClickListener {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Summary"))
                    }
                } else {
                    summaryText.text = "Failed to generate summary. Please try again."
                }
            }
        } else {
            Toast.makeText(context, "Note is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSummaryText(text: String): CharSequence {
        val builder = SpannableStringBuilder()
        val lines = text.split("\n")

        for (line in lines) {
            var currentLine = line.trim()
            if (currentLine.isEmpty()) {
                builder.append("\n")
                continue
            }

            val bulletRegex = "^([-•]|\\*(?!\\*))\\s*".toRegex()
            if (currentLine.contains(bulletRegex)) {
                builder.append("  • ")
                currentLine = currentLine.replaceFirst(bulletRegex, "")
            }


            var lastIdx = 0
            val boldRegex = "\\*{2,3}(.*?)\\*{2,3}".toRegex()
            
            boldRegex.findAll(currentLine).forEach { match ->
                builder.append(currentLine.substring(lastIdx, match.range.first))
                
                val boldStart = builder.length
                val boldContent = match.groupValues[1]
                

                if (boldContent == "OVERVIEW" || boldContent == "KEY HIGHLIGHTS" || boldContent == "INSIGHT") {
                    builder.append(boldContent.uppercase())
                } else {
                    builder.append(boldContent)
                }
                
                builder.setSpan(StyleSpan(Typeface.BOLD), boldStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                lastIdx = match.range.last + 1
            }
            
            builder.append(currentLine.substring(lastIdx))
            builder.append("\n")
        }
        return builder
    }

    override fun onDestroy() {
        super.onDestroy()
        editNoteBinding = null
    }


}