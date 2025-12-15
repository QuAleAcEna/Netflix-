package com.example.netflix.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

class AuthStore(private val context: Context) {
    private val KEY_REMEMBER = booleanPreferencesKey("remember_me")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_PASSWORD = stringPreferencesKey("password")

    val rememberedCredentials: Flow<StoredCredentials?> = context.authDataStore.data.map { prefs ->
        if (prefs[KEY_REMEMBER] == true) {
            val user = prefs[KEY_USERNAME]
            val pass = prefs[KEY_PASSWORD]
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                StoredCredentials(user, pass)
            } else {
                null
            }
        } else null
    }

    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_REMEMBER] = remember
            if (remember) {
                prefs[KEY_USERNAME] = username
                prefs[KEY_PASSWORD] = password
            } else {
                prefs.remove(KEY_USERNAME)
                prefs.remove(KEY_PASSWORD)
            }
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

data class StoredCredentials(val username: String, val password: String)
