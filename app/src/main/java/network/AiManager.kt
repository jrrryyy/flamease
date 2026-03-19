package com.example.flamease

import android.content.Context
import android.util.Log
import com.example.flameease.Content
import com.example.flameease.GeminiRequest
import com.example.flameease.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AiManager private constructor(private val context: Context) {

    private val apiService: ApiService

    companion object {
        @Volatile
        private var INSTANCE: AiManager? = null

        fun getInstance(context: Context): AiManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AiManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    private fun getApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        Log.d("AiManager", "API Key length: ${key.length}, starts with quote: ${key.startsWith("\"")}")
        return key
    }
    private fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    suspend fun getAIResponse(userMessage: String, conversationHistory: List<Message>): String {
        return withContext(Dispatchers.IO) {
            try {
                val contents = mutableListOf<Content>()
                val recentHistory = conversationHistory.takeLast(6)

                for (message in recentHistory) {
                    val role = if (message.isUser) "user" else "model"
                    contents.add(Content(parts = listOf(Part(message.text)), role = role))
                }

                contents.add(Content(parts = listOf(Part(userMessage)), role = "user"))

                val request = GeminiRequest(contents = contents)
                val apiKey = getApiKey()
                val response = apiService.getGeminiResponse(apiKey, request)

                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Sorry, I couldn't process that. Try asking about room bookings!"

            } catch (e: Exception) {
            Log.e("AiManager", "API Error: ${e.message}")
            "Error: ${e.localizedMessage}" // This will tell you exactly what's wrong on your screen
        }
        }
    }
}