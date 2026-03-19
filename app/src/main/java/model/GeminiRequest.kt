package com.example.flameease

data class GeminiRequest(
    val contents: List<Content> = emptyList()
)

data class Content(
    val parts: List<Part> = emptyList(),
    val role: String? = null
)

data class Part(
    val text: String
)

data class GeminiResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: Content
)