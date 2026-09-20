package com.nusafit.app

import android.content.Context

/**
 * Firebase-free sync adapter.
 * NusaFit stores activities locally on the device; no Firebase configuration is required.
 */
object FirebaseSync {
    fun available(): Boolean = false

    fun syncActivity(
        c: Context,
        a: ActivityRecord,
        media: List<String> = emptyList(),
        done: (Boolean) -> Unit = {}
    ) {
        done(false)
    }
}

object AppContext {
    lateinit var ctx: Context
}
