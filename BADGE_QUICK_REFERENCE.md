# QUICK REFERENCE: Persistent Notification Badge

## 🎯 What Changed?
Badge now appears on **all activities**, not just notifications.

## 🔧 Implementation

### New Component
- **`BadgeManager.kt`** - Centralized badge update utility
  - Location: `app/src/main/java/com/example/flamease/BadgeManager.kt`
  - Usage: `BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)`

### Modified Activities (Added onResume)
```
faculty.kt          ✅
request.kt          ✅
notifications.kt    ✅
Settings.kt         ✅
Buildings.kt        ✅
Rooms.kt            ✅
```

## 📋 How It Works

1. User navigates to any activity
2. `onResume()` is called automatically by Android
3. BadgeManager fetches fresh unread count from Firestore
4. Badge updates with current count
5. Process repeats when user returns to activity

## 🚀 To Use in New Activities

```kotlin
// 1. Add field
private lateinit var tvNotifBadge: TextView

// 2. Add onResume
override fun onResume() {
    super.onResume()
    BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)
}

// 3. Layout must have: android:id="@+id/tvNotifBadge"
```

## ✅ Testing

- [ ] Open any activity
- [ ] Verify badge shows if unread count > 0
- [ ] Navigate to different activity
- [ ] Badge still visible with same count
- [ ] Go to notifications and mark as read
- [ ] Navigate back
- [ ] Badge count decreased/disappeared

## 📊 Badge Display Rules

| Count | Display |
|-------|---------|
| 0 | Hidden (GONE) |
| 1-99 | Shows number |
| 100+ | Shows "99+" |

## 🐛 Debugging

**Badge not showing?**
- Check: Is `tvNotifBadge` TextView in your activity layout XML?
- Check: Is `notSeen` field being set correctly in Firestore?
- Check: Is user authenticated (currentUser != null)?

**Badge stuck at old count?**
- Force refresh: Navigate away and back to activity
- onResume() will fetch latest data from Firestore

**Badge showing on wrong activity?**
- Verify activity layout has correct `R.id.tvNotifBadge` ID
- Check BadgeManager is called in onResume()

## 📁 Files to Reference
- Badge Update Logic: `BadgeManager.kt`
- Reference Implementation: `notifications.kt` (line 32-36)
- Data Model: `model/RequestData.kt` (field: `notSeen`)
- Sample Activity: `faculty.kt` (line 35-39)

