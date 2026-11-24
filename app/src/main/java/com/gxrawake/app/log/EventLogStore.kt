package com.gxrawake.app.log

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object EventLogStore {
    const val PREFS_NAME = "gxr_awake_events"
    private const val KEY_EVENTS = "events"
    private const val MAX_LINES = 120

    private val lock = Any()

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun append(context: Context, message: String) {
        if (message.isBlank()) return
        synchronized(lock) {
            val sp = prefs(context)
            val lines = ArrayDeque<String>()
            val existing = sp.getString(KEY_EVENTS, "").orEmpty()
            if (existing.isNotEmpty()) {
                for (line in existing.split('\n')) {
                    if (line.isNotEmpty()) {
                        lines.addLast(line)
                    }
                }
            }
            val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val sanitized = message.replace('\n', ' ').trim()
            lines.addLast("$timestamp  $sanitized")
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
            }
            sp.edit().putString(KEY_EVENTS, lines.joinToString("\n")).apply()
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        prefs(context).getString(KEY_EVENTS, "").orEmpty()
    }

    fun readNewestFirst(context: Context): String {
        val raw = read(context)
        if (raw.isEmpty()) return ""
        return raw.split('\n').filter { it.isNotEmpty() }.asReversed().joinToString("\n")
    }

    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit().remove(KEY_EVENTS).apply()
        }
    }
}
