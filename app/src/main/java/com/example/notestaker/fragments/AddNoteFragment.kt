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
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import com.example.notestaker.MainActivity
import com.example.notestaker.R
import com.example.notestaker.databinding.FragmentAddNoteBinding
import com.example.notestaker.model.Note
import com.example.notestaker.viewmodel.NoteViewModel
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar

class AddNoteFragment : Fragment(R.layout.fragment_add_note) , MenuProvider{
    private var addNoteBinding: FragmentAddNoteBinding? = null
    private val binding get() = addNoteBinding!!

    private lateinit var noteViewModel: NoteViewModel
    private lateinit var addNoteView: View

    private var selectedUnlockTimestamp: Long = 0L


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        addNoteBinding = FragmentAddNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        noteViewModel = (activity as MainActivity).noteViewModel
        addNoteView = view
    }

    private fun saveNote(view: View){
        val noteTitle = binding.addNoteTitle.text.toString().trim()
        val noteDesc = binding.addNoteDesc.text.toString().trim()

        if (noteTitle.isNotEmpty()){
            val note = Note(0, noteTitle, noteDesc, selectedUnlockTimestamp)
            noteViewModel.addNote(note)

            Toast.makeText(addNoteView.context, "Note Saved", Toast.LENGTH_SHORT).show()
            view.findNavController().popBackStack(R.id.homeFragment, false)

//            Snackbar.make(view, "Note saved successfully", Snackbar.LENGTH_SHORT).show()
//            view.findNavController().navigate(R.id.action_addNoteFragment_to_homeFragment)
        } else{
            Toast.makeText(addNoteView.context, "Please enter a title", Toast.LENGTH_SHORT).show()
        }

        }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
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
        menuInflater.inflate(R.menu.menu_add_note, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when(menuItem.itemId){
            R.id.saveMenu -> {
                saveNote(addNoteView)
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
        addNoteBinding = null
    }
}
