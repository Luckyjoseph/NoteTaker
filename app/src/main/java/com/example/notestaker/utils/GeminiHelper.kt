package com.example.notestaker.utils

import com.example.notestaker.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

object GeminiHelper {
    private val model = GenerativeModel(
        modelName = "models/gemini-3.6-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun summarize(text: String): String? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return "Error: Gemini API key is missing."
        }

        return try {
            val response = model.generateContent(
                content {
                    text("""
                        Please provide a professional executive summary of the following text. 
                        Structure it clearly with these sections:
                        
                        - **OVERVIEW**: A brief one-sentence summary.
                        - **KEY HIGHLIGHTS**: The most important points as clear, professional bullets.
                        - **INSIGHT**: A professional concluding thought.
                        
                        Use a formal and sophisticated tone. 
                        Ensure headers are in BOLD using **HEADER NAME**.
                        
                        Text to summarize:
                        $text
                    """.trimIndent())
                }
            )
            response.text ?: "Error: Empty response from AI."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
