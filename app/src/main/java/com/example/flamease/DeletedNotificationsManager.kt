package com.example.flamease

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deleted_notifications")

class DeletedNotificationsManager(private val context: Context) {
    companion object {
        private val DELETED_IDS_KEY = stringSetPreferencesKey("deleted_notification_ids")
    }

    val deletedIdsFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[DELETED_IDS_KEY] ?: emptySet() }

    suspend fun markAsDeleted(ids: List<String>) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[DELETED_IDS_KEY] ?: emptySet()
            preferences[DELETED_IDS_KEY] = currentSet + ids
        }
    }
}