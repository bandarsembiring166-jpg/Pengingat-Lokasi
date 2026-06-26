package com.example.security

import android.content.Context
import android.content.SharedPreferences

class SecurePreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "secure_user_profile_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_ID_TOKEN = "id_token"
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var displayName: String?
        get() {
            val encrypted = prefs.getString(KEY_DISPLAY_NAME, null) ?: return null
            return CryptoManager.decrypt(encrypted)
        }
        set(value) {
            val encrypted = value?.let { CryptoManager.encrypt(it) }
            prefs.edit().putString(KEY_DISPLAY_NAME, encrypted).apply()
        }

    var email: String?
        get() {
            val encrypted = prefs.getString(KEY_EMAIL, null) ?: return null
            return CryptoManager.decrypt(encrypted)
        }
        set(value) {
            val encrypted = value?.let { CryptoManager.encrypt(it) }
            prefs.edit().putString(KEY_EMAIL, encrypted).apply()
        }

    var photoUrl: String?
        get() {
            val encrypted = prefs.getString(KEY_PHOTO_URL, null) ?: return null
            return CryptoManager.decrypt(encrypted)
        }
        set(value) {
            val encrypted = value?.let { CryptoManager.encrypt(it) }
            prefs.edit().putString(KEY_PHOTO_URL, encrypted).apply()
        }

    var idToken: String?
        get() {
            val encrypted = prefs.getString(KEY_ID_TOKEN, null) ?: return null
            return CryptoManager.decrypt(encrypted)
        }
        set(value) {
            val encrypted = value?.let { CryptoManager.encrypt(it) }
            prefs.edit().putString(KEY_ID_TOKEN, encrypted).apply()
        }

    fun clearProfile() {
        prefs.edit().clear().apply()
    }
}
