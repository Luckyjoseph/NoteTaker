package com.example.notestaker.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.example.notestaker.MainActivity
import com.example.notestaker.R
import com.example.notestaker.databinding.FragmentEditNoteBinding
import com.example.notestaker.model.Note
import com.example.notestaker.viewmodel.NoteViewModel
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
        // Inflate the layout for this fragment
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
            else -> false
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        editNoteBinding = null
    }


}