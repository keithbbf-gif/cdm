package com.cosmos.cdm.data

import android.content.Context

/**
 * Persists the COSMOS server URL only.
 *
 * The bearer token is NEVER written to disk (SharedPreferences, files, or
 * backup). A stored token on a phone outlives the session and the user's
 * intent — paste-per-session is the cost of not parking a credential on the
 * handset. Same rule as VMC and cDeck.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun serverUrl(): String = prefs.getString(KEY_URL, DEFAULT_URL)?.trim().orEmpty()
        .ifBlank { DEFAULT_URL }

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_URL, url.trim()).apply()
    }

    companion object {
        const val PREFS = "cdm_settings"
        const val KEY_URL = "server_url"
        const val DEFAULT_URL = "http://100.103.9.112:8791"
        const val LAN_URL = "http://192.168.1.10:8791"
        const val TAILSCALE_URL = "http://100.103.9.112:8791"
    }
}
