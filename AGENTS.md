# AGENTS.md - Guide for AI Coding Agents

## Project Overview
**Flamease** is an Android Room Booking & Facility Management application using Kotlin, Firestore, and Firebase Auth. It features multi-role support (Student/Faculty/Admin), real-time booking requests, AI-assisted help system (Gemini), and notification badge systems.

---

## Critical Architecture Decisions

### 1. **Firebase Firestore as Primary Database**
- **Why**: Real-time updates, document-based structure matches room/building/request data model
- **Collection Schema**:
  - `users/` - User profiles with `role` (student/faculty/admin), `status`, `idNumber`, `email`
  - `room_requests/` - Booking requests with fields: `requestId`, `room`, `building`, `status` (approved/pending/rejected), `userId`, `notSeen` (badge flag), `createdAt`, `bookingDate`
  - `buildings/` - Building data with `id`, `name`, `roomCount`
  - `rooms/` - Room data (nested under buildings or separate)
- **Key Pattern**: Use `.whereEqualTo("userId", idNumber)` for user-scoped queries across activities

### 2. **No Repository Pattern - Direct Firestore Queries**
- **Current State**: Each activity executes Firestore queries directly (see `notifications.kt`, `faculty.kt`, `Settings.kt`)
- **Pattern**: Use `db.collection("room_requests").whereEqualTo("userId", idNumber).get()` with `.addOnSuccessListener { snapshot -> ... }`
- **Implication**: Changes to data structure require multi-file updates across 6+ activities

### 3. **MVVM Lightweight Implementation**
- **ViewModels**: Only `FacultyViewModel` and `LoginViewModel` exist—not fully adopted across app
- **Pattern**: Most activities manage state directly in Activity, not ViewModel
- **When to extend**: New complex flows should add ViewModel to `viewmodel/` package

### 4. **Notification Badge Logic - Persistent Across All Activities**
**Pattern**: Use `BadgeManager.updateBadgeCount()` in `onResume()` of every activity
```kotlin
// In EVERY activity's onResume():
override fun onResume() {
    super.onResume()
    BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)
}

// BadgeManager.kt handles all the Firestore logic:
object BadgeManager {
    fun updateBadgeCount(activity: Activity, badgeTextViewId: Int) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val idNumber = userDoc.getString("idNumber") ?: ""
                db.collection("room_requests")
                    .whereEqualTo("userId", idNumber)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val unreadCount = snapshot.mapNotNull { 
                            doc.toObject(RequestData::class.java)
                        }.count { it.notSeen == true }
                        tvNotifBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                        tvNotifBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                    }
            }
    }
}
```
- **Key File**: `BadgeManager.kt` (centralized utility)
- **Implemented in**: `faculty.kt`, `request.kt`, `notifications.kt`, `Settings.kt`, `Buildings.kt`, `Rooms.kt`
- **Update Trigger**: Badge updates every time activity becomes visible via `onResume()`
- **Result**: Badge now appears consistently on nav bar icon across all activities

---

## Component Organization

### Activities (`app/src/main/java/com/example/flamease/`)
**Core User Flows**:
- `MainActivity.kt` - Launch/auth routing with SharedPreferences auto-login via `rememberMe` flag
- `Login.kt` / `createaccount.kt` - Auth with Firebase and OTP validation
- `onboarding1.kt` / `onboarding2.kt` - First-time user flow
- `faculty.kt` - Faculty dashboard showing requests; implements badge logic
- `request.kt` - Student request view; implements badge logic + `markAsRead()`
- `notifications.kt` - Notification center with delete/select-all; reference badge implementation
- `Settings.kt` / `Buildings.kt` / `Rooms.kt` - Catalog browsing; implement badge via Firestore query

**Supporting Flows**:
- `help_support.kt` - AI help using Gemini API (see `network/AiManager.kt`)
- `room_request.kt` - Create new booking request
- `privacy_policy.kt` / `forget_password.kt` - Auxiliary screens

### Models (`app/src/main/java/model/`)
- `RequestData.kt` - Core: `room`, `building`, `status`, `userId`, **`notSeen`** (badge), `createdAt`, `bookingDate`
- `Building.kt` - `id`, `name`, `roomCount`
- `RoomData.kt` - Room attributes
- `Message.kt` - AI chat history: `text`, `isUser`, `timestamp`
- `GeminiRequest.kt` - API request wrapper for Gemini

### Adapters (`app/src/main/java/adapter/`)
- `RequestAdapter.kt` / `AllRequestsAdapter.kt` - Request list rendering
  - **Key Method**: `getUnreadCount()` returns `requestList.count { it.notSeen == true }`
  - **Styling**: Status color-coded (approved=green, pending=orange, rejected=red)
- `NotificationAdapter.kt` - Notification deletion UI with checkbox support
- `BuildingAdapter.kt` / `RoomAdapter.kt` - Catalog browsing

### Network (`app/src/main/java/network/`)
- `AiManager.kt` - Singleton managing Gemini API calls via Retrofit
  - Reads API key from `BuildConfig.GEMINI_API_KEY` (loaded from `gradle.properties`)
  - Base URL: `https://generativelanguage.googleapis.com/`
  - Endpoint: `/v1beta/models/gemini-1.5-flash:generateContent`
- `ApiService.kt` - Retrofit interface for Gemini requests

### Utilities
- `DeletedNotificationsManager.kt` - SharedPreferences-based soft delete tracking (prevents refetch after deletion)
- `BadgeManager.kt` - Singleton for updating notification badge count across all activities (queries Firestore on each `onResume()`)

---

## Essential Development Workflows

### Building & Running
```bash
# Sync and build (IDEs do this automatically)
./gradlew build

# Debug APK installation
./gradlew installDebug

# Connect to Firebase emulator (if using local testing)
# Note: App currently uses production Firebase (check google-services.json)
```

### Key Build Configuration
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14)
- **JVM Target**: 11
- **Email credentials**: Embedded in `BuildConfig` (see `build.gradle.kts` debug/release sections)
- **API Keys**: `GEMINI_API_KEY` loaded from `gradle.properties`

### SharedPreferences Keys (Authentication State)
```kotlin
val prefs = getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
prefs.getBoolean("onboarding_finished", false)      // First-time user check
prefs.getBoolean("rememberMe", false)               // Auto-login flag
prefs.getString("idNumber", null)                   // User ID persistence
```

### Testing
- **Unit Tests**: `app/src/test/`
- **Instrumentation Tests**: `app/src/androidTest/` (limited; app needs expanded coverage)

---

## Common Extension Points

### Adding a New Activity Feature
1. **Create Activity** in `app/src/main/java/com/example/flamease/MyFeature.kt`
2. **Register in AndroidManifest.xml** (already templated for all core activities)
3. **Add navigation** in bottom nav button click listeners (see `notifications.kt` lines 75-90)
4. **If needs badge**: Add `tvNotifBadge` TextView and call `updateBadgeCount()` in `onResume()`

### Adding Firestore Query Logic
- **Pattern**: Use `db.collection("collection_name").whereEqualTo("field", value).get()`
- **Listen for updates**: Use `.addSnapshotListener()` instead of `.get()` for real-time updates (not yet used in codebase)
- **Handle errors**: All queries should include `.addOnFailureListener { e -> Log.e(...) }`

### Modifying RequestData Schema
- **Impact**: Update `model/RequestData.kt`, then cascade changes to:
  - All adapters that render requests
  - Activities with Firestore queries (badge counts, filtering logic)
  - Firestore indexes (if adding new filter fields like `status`, `building`)
- **Example**: `notSeen` field added to `RequestData` → required updates in 6+ files

### Integrating New AI Features
- Extend `AiManager.kt` with new methods calling `apiService` (Retrofit)
- Override `getApiKey()` to fetch from `BuildConfig` or alternative secret store
- **Current limitation**: Single model (Gemini 1.5 Flash); adding multi-model support requires API abstraction

---

## Project-Specific Conventions

### Naming & Casing
- **Activities**: PascalCase (e.g., `MainActivity`, `Settings`, `Buildings`)
- **Layout XML**: snake_case (e.g., `activity_notifications`, `item_request_card`)
- **Variables**: camelCase with type prefix (`tv` = TextView, `rv` = RecyclerView, `btn` = Button)
- **Kotlin packages**: Lower-case (e.g., `network`, `adapter`, `model`)

### Color & Status Values
- **Status field** (on RequestData): lowercase strings: `"approved"`, `"pending"`, `"rejected"`
- **Status display**: Converted to `.uppercase()` for UI
- **Color mapping** (see `RequestAdapter.kt`):
  - Approved: `#2ECC71` (green) + `R.drawable.bg_badge_approved`
  - Pending: `#E8B547` (orange) + `R.drawable.bg_badge_pending`
  - Rejected: `#E74C3C` (red) + `R.drawable.bg_badge_rejected`

### Date Formatting
```kotlin
val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
val date = request.createdAt?.toDate()  // Firebase Timestamp to Date
val formatted = sdf.format(date)
```

### Mode Enforcement
- **App enforces Light Mode globally** via `AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)` in `MainActivity.onCreate()`
- Don't add dark mode support without revisiting this setting

---

## Critical Files for Context

### Must-Read for New Features
- `AndroidManifest.xml` - All activity declarations and permissions
- `model/RequestData.kt` - Core data structure
- `app/build.gradle.kts` - Dependencies, build config, API keys
- `gradle/libs.versions.toml` - Firebase/Retrofit versions

### Reference Implementations
- **Badge Logic**: `notifications.kt` (lines 70-80), `faculty.kt`, `Settings.kt`
- **Firestore Queries**: `MainActivity.kt`, `notifications.kt` (lines 85-120)
- **AI Integration**: `help_support.kt` → calls `AiManager.getInstance()` → `getAIResponse()`
- **Bottom Navigation**: `notifications.kt` (lines 75-90)

---

## Known Limitations & Gotchas

1. **No Real-Time Listeners**: App uses one-shot `.get()` queries; UI won't auto-update when Firestore changes (add `.addSnapshotListener()` for live updates)
2. **No Pagination**: All queries fetch full result set into memory; large datasets will cause lag
3. **No Offline Support**: No Room database or local cache; offline = app non-functional
4. **Minimal Error Handling**: Network errors logged but not shown to user; add better UX feedback
5. **ViewModels Underused**: State lost on config changes in most activities
6. **Hard-coded API Keys**: `BuildConfig` in gradle files; consider Secret Management for production
7. **Light Mode Enforced**: `AppCompatDelegate.MODE_NIGHT_NO` globally prevents dark mode

---

## Dependency Tree (Key Libraries)
```
Firebase Suite:
  - firebase-auth (24.0.1)          → User authentication
  - firebase-firestore (26.1.1)      → Real-time database
  - google-gms-google-services       → Firebase setup
  
Networking:
  - retrofit2                         → HTTP client for Gemini API
  - okhttp3 + logging-interceptor    → HTTP debugging
  - gson                              → JSON serialization
  
UI/Android:
  - androidx.appcompat (1.7.1)       → Compatibility layer
  - androidx.constraintlayout (2.2.1)→ Responsive layouts
  - material (1.13.0)                → Material Design components
  - androidx.activity (1.12.3)       → Modern Activity APIs
```

---

## Quick Debugging Tips

- **SharedPreferences**: Check `getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)` for auth state
- **Firestore Rules**: If 401/403 errors, check Firebase Console Security Rules (likely require `request.auth.uid == userId`)
- **HTTP Logging**: `AiManager` has `HttpLoggingInterceptor.Level.BODY` enabled; check Logcat for API payloads
- **BuildConfig**: If API key issues, verify `GEMINI_API_KEY` in `gradle.properties` and rebuild (clean build if cache stale)
- **Intent Navigation**: Check `AndroidManifest.xml` for activity names; mismatch with `Intent(this, ClassName::class.java)` will crash

