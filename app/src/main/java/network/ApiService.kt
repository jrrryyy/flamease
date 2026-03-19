package network

import com.example.flameease.GeminiRequest
import com.example.flameease.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Replace the previous ApiService with this:
interface ApiService {
    // Removed "-latest" and added a leading slash for safety
    // (if your baseUrl is "https://generativelanguage.googleapis.com/")
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun getGeminiResponse(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}