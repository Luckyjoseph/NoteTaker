package com.example.notestaker.utils

import android.util.Log
import com.example.notestaker.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

object GeminiHelper {
    private const val TAG = "GeminiHelper"
    private val model = GenerativeModel(
        modelName = "models/gemini-3.6-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun summarize(text: String): String? {
        Log.d(TAG, "Summarizing text: ${text.take(20)}...")
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            Log.e(TAG, "Gemini API key is missing.")
            return "Error: Gemini API key is missing."
        }

        return try {
            val response = model.generateContent(
                content {
                    text("""
                        Please provide a professional executive summary of the following text. 
                        Structure it clearly with these sections:
                        
                        **OVERVIEW**: A brief one-sentence summary.
                        **KEY HIGHLIGHTS**: The most important points as clear, professional bullets.
                        **INSIGHT**: A professional concluding thought.
                        
                        Use a formal and sophisticated tone. 
                        Ensure headers are in BOLD using **HEADER NAME**.
                        
                        Text to summarize:
                        $text
                    """.trimIndent())
                }
            )
            Log.d(TAG, "Summarize response received")
            response.text ?: "Error: Empty response from AI."
        } catch (e: Exception) {
            Log.e(TAG, "Summarize error: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    suspend fun generateTitle(text: String): String? {
        Log.d(TAG, "Generating title for: ${text.take(20)}...")
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            Log.e(TAG, "Gemini API key is missing.")
            return null
        }
        return try {
            val response = model.generateContent(
                content {
                    text("Generate a short, concise title (max 5 words) for the following note. Return ONLY the title text, no quotes or prefix: $text")
                }
            )
            Log.d(TAG, "Title generated: ${response.text}")
            response.text?.trim()?.removeSurrounding("\"")
        } catch (e: Exception) {
            Log.e(TAG, "Generate title error: ${e.message}", e)
            null
        }
    }

    suspend fun analyzeSentiment(text: String): String {
        Log.d(TAG, "Analyzing sentiment for: ${text.take(20)}...")
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return "NEUTRAL"
        return try {
            val response = model.generateContent(
                content {
                    text("Analyze the sentiment of this text and return exactly one word from this list (HAPPY, SAD, ANGRY, NEUTRAL): $text")
                }
            )
            val mood = response.text?.trim()?.uppercase() ?: "NEUTRAL"
            Log.d(TAG, "Sentiment analyzed: $mood")
            if (mood in listOf("HAPPY", "SAD", "ANGRY", "NEUTRAL")) mood else "NEUTRAL"
        } catch (e: Exception) {
            Log.e(TAG, "Analyze sentiment error: ${e.message}", e)
            "NEUTRAL"
        }
    }

    suspend fun suggestTags(text: String): String? {
        Log.d(TAG, "Suggesting tags for: ${text.take(20)}...")
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        return try {
            val response = model.generateContent(
                content {
                    text("Suggest 3-5 relevant hashtags for the following note content. Return ONLY the hashtags separated by spaces: $text")
                }
            )
            Log.d(TAG, "Tags suggested: ${response.text}")
            response.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Suggest tags error: ${e.message}", e)
            null
        }
    }

    data class RefinedNote(val title: String?, val tags: String?, val mood: String)

    suspend fun refineNote(text: String): RefinedNote {
        Log.d(TAG, "Performing Super Refine for: ${text.take(20)}...")
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return RefinedNote(null, null, "NEUTRAL")

        return try {
            val response = model.generateContent(
                content {
                    text("""
                        Analyze this note text and provide three specific things:
                        1. A concise title (max 5 words).
                        2. 3-5 relevant hashtags.
                        3. A single-word mood (HAPPY, SAD, ANGRY, or NEUTRAL).
                        
                        Format your response EXACTLY like this:
                        TITLE: [title here]
                        TAGS: [tags here]
                        MOOD: [mood here]
                        
                        Note text:
                        $text
                    """.trimIndent())
                }
            )

            val responseText = response.text ?: ""
            val title = "TITLE: (.*)".toRegex().find(responseText)?.groupValues?.get(1)?.trim()
            val tags = "TAGS: (.*)".toRegex().find(responseText)?.groupValues?.get(1)?.trim()
            val mood = "MOOD: (.*)".toRegex().find(responseText)?.groupValues?.get(1)?.trim()?.uppercase() ?: "NEUTRAL"

            Log.d(TAG, "Super Refine success: Title=$title, Mood=$mood")
            RefinedNote(title, tags, if (mood in listOf("HAPPY", "SAD", "ANGRY", "NEUTRAL")) mood else "NEUTRAL")
        } catch (e: Exception) {
            Log.e(TAG, "Super Refine error: ${e.message}")
            if (e.message?.contains("quota", ignoreCase = true) == true) {
                return RefinedNote("Quota Exceeded", "Please wait", "NEUTRAL")
            }
            RefinedNote(null, null, "NEUTRAL")
        }
    }
}
