package com.example.notestaker.repository

import com.example.notestaker.database.NoteData
import com.example.notestaker.model.Note

class NoteRepository(private val db: NoteData) {

    suspend fun insertNote(note: Note) = db.getNoteDao().insertNote(note)
    suspend fun updateNote(note: Note) = db.getNoteDao().updateNote(note)
    suspend fun deleteNote(note: Note) =db.getNoteDao().deleteNote(note)


    fun getAllNotes() = db.getNoteDao().getAllNotes()
    fun searchNote(query: String?) =db.getNoteDao().searchNote(query)
}