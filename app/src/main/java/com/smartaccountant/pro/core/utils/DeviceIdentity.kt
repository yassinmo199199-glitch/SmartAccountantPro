package com.smartaccountant.pro.core.utils

import android.content.Context
import android.os.Build
import java.util.UUID

object DeviceIdentity {
    private const val PREFERENCES = "device_identity"
    private const val INSTALLATION_ID = "installation_id"

    fun displayName(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val installationId = preferences.getString(INSTALLATION_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(INSTALLATION_ID, it).apply()
        }
        val model = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
        return "$model · ${installationId.take(8)}"
    }

    fun legacyName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
