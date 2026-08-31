package com.example.notestaker.fragments

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.notestaker.MainActivity
import com.example.notestaker.R
import com.example.notestaker.databinding.FragmentAddNoteBinding
import com.example.notestaker.model.Note
import com.example.notestaker.utils.GeminiHelper
import com.example.notestaker.viewmodel.NoteViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

class AddNoteFragment : Fragment(R.layout.fragment_add_note), MenuProvider {
    private var addNoteBinding: FragmentAddNoteBinding? = null
    private val binding get() = addNoteBinding!!

    private lateinit var noteViewModel: NoteViewModel
    private lateinit var addNoteView: View

    private var selectedUnlockTimestamp: Long = 0L
    private var isSaving = false
    private var isAIProcessing = false
    private var previousDescription: String? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false

    private var photoFile: File? = null
    private var photoUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Permission denied to record audio", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            processScannedImage()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        addNoteBinding = FragmentAddNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        noteViewModel = (activity as MainActivity).noteViewModel
        addNoteView = view

        binding.recordFab.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionAndStartRecording()
            }
        }

        binding.scanFab.setOnClickListener {
            checkCameraPermissionAndOpen()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val storageDir = File(requireContext().cacheDir, "images")
        if (!storageDir.exists()) storageDir.mkdirs()
        photoFile = File(storageDir, "scan_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile!!)
        takePictureLauncher.launch(photoUri)
    }

    private fun processScannedImage() {
        val file = photoFile ?: return
        if (!file.exists()) return

        lifecycleScope.launch {
            try {
                Toast.makeText(context, "AI is scanning text from image...", Toast.LENGTH_LONG).show()
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val text = GeminiHelper.scanTextFromImage(bitmap)
                if (!text.isNullOrEmpty()) {
                    binding.addNoteDesc.setText(text)
                    Toast.makeText(context, "Text scanned successfully!", Toast.LENGTH_SHORT).show()
                    refineNoteWithAI() // Auto-refine to get a title and mood
                } else {
                    Toast.makeText(context, "No text found in image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error scanning image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        binding.recordFab.setImageResource(android.R.drawable.ic_media_pause)
        binding.recordFab.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (isRecording) stopRecording()
            }
            override fun onError(error: Int) {
                stopRecording()
                Toast.makeText(context, "Speech error: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    binding.addNoteDesc.setText(matches[0])
                    refineNoteWithAI() // Auto-refine when speech is finished
                }
                stopRecording()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    binding.addNoteDesc.setText(matches[0])
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
        Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        binding.recordFab.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.recordFab.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.orangeRed)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun saveNote(view: View) {
        if (isSaving) return
        
        val noteTitle = binding.addNoteTitle.text.toString().trim()
        val noteDesc = binding.addNoteDesc.text.toString().trim()

        if (noteDesc.isNotEmpty()) {
            isSaving = true
            if (noteTitle.isEmpty()) {
                Toast.makeText(context, "AI is generating a title and analyzing mood...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        val aiTitle = GeminiHelper.generateTitle(noteDesc)
                        val mood = GeminiHelper.analyzeSentiment(noteDesc)
                        finalizeSave(aiTitle ?: "Untitled AI Note", noteDesc, mood, view)
                    } catch (e: Exception) {
                        finalizeSave("Untitled AI Note", noteDesc, "NEUTRAL", view)
                    }
                }
            } else {
                Toast.makeText(context, "AI is analyzing mood...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        val mood = GeminiHelper.analyzeSentiment(noteDesc)
                        finalizeSave(noteTitle, noteDesc, mood, view)
                    } catch (e: Exception) {
                        finalizeSave(noteTitle, noteDesc, "NEUTRAL", view)
                    }
                }
            }
        } else {
            Toast.makeText(addNoteView.context, "Please enter a description", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finalizeSave(title: String, desc: String, mood: String, view: View) {
        val note = Note(0, title, desc, selectedUnlockTimestamp, mood)
        noteViewModel.addNote(note)
        Toast.makeText(addNoteView.context, "Note Saved", Toast.LENGTH_SHORT).show()
        view.findNavController().popBackStack(R.id.homeFragment, false)
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
        return when (menuItem.itemId) {
            R.id.saveMenu -> {
                saveNote(addNoteView)
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
            R.id.aiRefineMenu -> {
                refineNoteWithAI()
                true
            }
            else -> false
        }
    }

    private fun refineNoteWithAI() {
        if (isAIProcessing) return
        val noteDesc = binding.addNoteDesc.text.toString().trim()
        if (noteDesc.isNotEmpty()) {
            isAIProcessing = true
            Toast.makeText(context, "AI is refining your note...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val refined = GeminiHelper.refineNote(noteDesc)
                    if (refined.title != null) {
                        binding.addNoteTitle.setText(refined.title)
                    }
                    if (refined.tags != null) {
                        val currentDesc = binding.addNoteDesc.text.toString()
                        if (!currentDesc.contains(refined.tags)) {
                            binding.addNoteDesc.setText("$currentDesc\n\n${refined.tags}")
                        }
                    }
                    Toast.makeText(context, "Note refined! Mood: ${refined.mood}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error refining note: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isAIProcessing = false
                }
            }
        } else {
            Toast.makeText(context, "Note is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun summarizeNote() {
        val noteDesc = binding.addNoteDesc.text.toString().trim()
        if (noteDesc.isNotEmpty()) {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)

            val dialog = BottomSheetDialog(requireContext())
            val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_summary, null)
            dialog.setContentView(sheetView)

            // Force the bottom sheet to be fully expanded
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

            val summaryText = sheetView.findViewById<TextView>(R.id.summaryText)
            val progressBar = sheetView.findViewById<View>(R.id.summaryProgressBar)
            val copyButton = sheetView.findViewById<Button>(R.id.copyButton)
            val shareButton = sheetView.findViewById<Button>(R.id.shareButton)
            val doneGoHomeButton = sheetView.findViewById<Button>(R.id.doneGoHomeButton)
            val replaceButton = sheetView.findViewById<Button>(R.id.replaceButton)

            progressBar.visibility = View.VISIBLE
            summaryText.visibility = View.GONE
            copyButton.isEnabled = false
            shareButton.isEnabled = false
            doneGoHomeButton.isEnabled = false
            replaceButton.isEnabled = false

            dialog.show()

            lifecycleScope.launch {
                val summary = GeminiHelper.summarize(noteDesc)
                progressBar.visibility = View.GONE
                summaryText.visibility = View.VISIBLE

                if (summary != null) {
                    summaryText.text = formatSummaryText(summary)
                    copyButton.isEnabled = true
                    shareButton.isEnabled = true
                    doneGoHomeButton.isEnabled = true
                    replaceButton.isEnabled = true

                    replaceButton.setOnClickListener {
                        previousDescription = binding.addNoteDesc.text.toString()
                        binding.addNoteDesc.setText(summary)
                        dialog.dismiss()

                        Snackbar.make(binding.root, "Note replaced with summary", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                binding.addNoteDesc.setText(previousDescription)
                                Toast.makeText(context, "Reverted to previous version", Toast.LENGTH_SHORT).show()
                            }.show()
                    }

                    doneGoHomeButton.setOnClickListener {
                        dialog.dismiss()
                        addNoteView.findNavController().popBackStack(R.id.homeFragment, false)
                    }

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
        speechRecognizer?.destroy()
        addNoteBinding = null
    }
}
