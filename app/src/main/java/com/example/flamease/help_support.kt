package com.example.flamease

import adapter.MessageAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.AiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class help_support : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var isTyping = false
    private lateinit var aiManager: AiManager  // ✅ Added here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_help_support)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        aiManager = AiManager.getInstance(this)  // ✅ Initialize AI Manager

        initViews()
        setupRecyclerView()
        setupClickListeners()
        showWelcomeMessage()
    }

    private fun initViews() {
        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.welcomeView).visibility = View.VISIBLE
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages)
        rvChat.apply {
            layoutManager = LinearLayoutManager(this@help_support).apply {
                stackFromEnd = true
            }
            adapter = this@help_support.adapter
        }
    }

    private fun setupClickListeners() {
        btnSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        // Suggestion buttons
        findViewById<Button>(R.id.btnSuggest1).setOnClickListener {
            sendSuggestedMessage("How to request a room?")
        }
        findViewById<Button>(R.id.btnSuggest2).setOnClickListener {
            sendSuggestedMessage("See pending requests?")
        }
    }

    private fun showWelcomeMessage() {
        Handler(Looper.getMainLooper()).postDelayed({
            findViewById<View>(R.id.welcomeView).visibility = View.GONE
            addAIMessage("Hello! 👋 Welcome to FlameEase Support. How can I help you today?")
        }, 500)
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        // Hide keyboard
        hideKeyboard()

        // Add user message
        addUserMessage(messageText)
        etMessage.text?.clear()

        // Show typing indicator
        showTypingIndicator()

        // Get AI response using REAL AI ✅
        CoroutineScope(Dispatchers.IO).launch {
            val aiResponse = getAIResponse(messageText)
            withContext(Dispatchers.Main) {
                hideTypingIndicator()
                addAIMessage(aiResponse)
                scrollToBottom()
            }
        }
    }

    private fun sendSuggestedMessage(text: String) {
        findViewById<HorizontalScrollView>(R.id.suggestionLayout).visibility = View.GONE
        addUserMessage(text)
        CoroutineScope(Dispatchers.IO).launch {
            val aiResponse = getAIResponse(text)
            withContext(Dispatchers.Main) {
                addAIMessage(aiResponse)
                scrollToBottom()
            }
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(Message(text, true, System.currentTimeMillis()))  // ✅ Add timestamp
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun addAIMessage(text: String) {
        messages.add(Message(text, false, System.currentTimeMillis()))  // ✅ Add timestamp
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun showTypingIndicator() {
        isTyping = true
        // Add typing indicator view here if needed
    }

    private fun hideTypingIndicator() {
        isTyping = false
    }

    private fun scrollToBottom() {
        rvChat.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    // ✅ SINGLE AI Response method using REAL Gemini AI
    private suspend fun getAIResponse(userMessage: String): String {
        return try {
            aiManager.getAIResponse(userMessage, messages)
        } catch (e: Exception) {
            // Fallback to hardcoded responses if AI fails
            kotlinx.coroutines.delay(1000)
            when {
                userMessage.lowercase().contains("room") || userMessage.lowercase().contains("request") -> {
                    "To request a room:\n\n" +
                            "1. Tap the '+' button on Home screen\n" +
                            "2. Select room type & dates\n" +
                            "3. Fill guest details\n" +
                            "4. Review & confirm payment\n\n" +
                            "Your request will be reviewed within 24 hours! 🏠✨"
                }
                userMessage.lowercase().contains("pending") || userMessage.lowercase().contains("status") -> {
                    "To check pending requests:\n\n" +
                            "1. Go to 'My Requests' tab\n" +
                            "2. Tap on any request card\n" +
                            "3. View current status & details\n\n" +
                            "You can also get notifications! 📱🔔"
                }
                else -> "Thanks for reaching out! I'm experiencing technical difficulties. Please try again or contact support@flameease.com 😊"
            }
        }
    }
}