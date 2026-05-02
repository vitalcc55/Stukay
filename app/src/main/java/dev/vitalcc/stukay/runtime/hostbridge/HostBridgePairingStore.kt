package dev.vitalcc.stukay.runtime.hostbridge

import android.content.Context

interface HostBridgePairingStore {
    fun load(): String?

    fun save(rawPayload: String)

    fun clear()
}

class InMemoryHostBridgePairingStore(
    private var rawPayload: String? = null,
) : HostBridgePairingStore {
    override fun load(): String? = rawPayload

    override fun save(rawPayload: String) {
        this.rawPayload = rawPayload
    }

    override fun clear() {
        rawPayload = null
    }
}

class SharedPreferencesHostBridgePairingStore(
    context: Context,
) : HostBridgePairingStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = preferences.getString(KEY_PAIRING_PAYLOAD, null)

    override fun save(rawPayload: String) {
        preferences.edit()
            .putString(KEY_PAIRING_PAYLOAD, rawPayload)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_PAIRING_PAYLOAD)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "stukay_host_bridge"
        const val KEY_PAIRING_PAYLOAD = "pairing_payload"
    }
}
