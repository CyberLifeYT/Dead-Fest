package com.deadfest

import com.deadfest.util.I18n
import java.io.File
import java.util.Locale

/**
 * Points [I18n] at the real English strings.xml so JVM unit tests can call code that uses `tr()`.
 *
 * Mirrors the parts of Android's resource rules the app relies on: a value wrapped in double quotes
 * keeps its edge spaces, anything else is trimmed, and \' \" &amp; are unescaped. Formatting goes
 * through String.format, which shares Android's %1$s / %1$d / %% syntax.
 */
object TestStrings {
    private val byId: Map<Int, String> by lazy {
        val file = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File).first { it.exists() }
        val byName = Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        Class.forName("com.deadfest.R\$string").fields
            .filter { it.name in byName }
            .associate { it.getInt(null) to byName.getValue(it.name) }
    }

    private fun unescape(raw: String): String {
        val quoted = raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")
        val body = if (quoted) raw.substring(1, raw.length - 1) else raw.trim().replace(Regex("""\s+"""), " ")
        return body.replace("""\'""", "'").replace("""\"""", "\"").replace("""\n""", "\n")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }

    fun install() {
        I18n.lookup = { id, args ->
            val value = byId[id] ?: error("No English string for id $id")
            if (args.isEmpty()) value else String.format(Locale.US, value, *args)
        }
    }
}
