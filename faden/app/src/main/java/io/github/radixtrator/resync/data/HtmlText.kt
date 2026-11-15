package io.github.radixtrator.resync.data

import android.text.Html

/** An HTML tag, for stripping markup down to its text. */
val HTML_TAG = Regex("<[^>]*>")

/** A body's HTML as plain text for speech and notifications, or [fallback] when there's no HTML or it renders empty. */
fun plainText(html: String?, fallback: String): String =
    html?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }?.takeIf { it.isNotBlank() } ?: fallback
