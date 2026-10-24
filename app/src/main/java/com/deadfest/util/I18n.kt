package com.deadfest.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Translated text from anywhere: composables, click handlers, the ViewModel.
 *
 * Compose's `stringResource` only works inside a composable, but plenty of Dead-Fest's text is
 * produced elsewhere (snackbar messages, confirm-button handlers). [tr] reads the same
 * `strings.xml`, through the application context handed to [init] by DeadFestApplication. Changing
 * the phone's language recreates the screen, so text picks up the new language straight away.
 *
 * [lookup] is swappable so plain JVM unit tests can resolve ids from strings.xml without Android.
 */
object I18n {
    var lookup: (Int, Array<out Any?>) -> String = { _, _ -> error("I18n.init was never called") }

    fun init(context: Context) {
        val app = context.applicationContext ?: context
        lookup = { id, args -> if (args.isEmpty()) app.getString(id) else app.getString(id, *args) }
    }
}

fun tr(@StringRes id: Int): String = I18n.lookup(id, emptyArray())

fun tr(@StringRes id: Int, vararg args: Any?): String = I18n.lookup(id, args)
