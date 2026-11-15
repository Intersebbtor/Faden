package io.github.radixtrator.resync.i18n

import java.util.Locale

/**
 * Settings › General › "Language". [SYSTEM] follows the device (or Android 13+'s per-app language), falling back to
 * English for languages Faden has no translation for. Names are persisted, so entries keep theirs.
 */
enum class AppLanguage(val code: String?, val nativeName: String) {
    SYSTEM(null, "System"),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
}

/**
 * The app's text. Strings are written in English at the call site and looked up here: [t] for one string, [tn] for
 * one that changes with a count. English is the source, so a string with no translation simply stays English.
 *
 * Placeholders are `{0}`, `{1}`, …, so a translation can reorder them. The language is a process-wide setting:
 * [FadenApp] sets it at launch and the Language setting sets it then recreates the activity, so every screen reads
 * the new language as it's built again.
 */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.ENGLISH
        private set

    /** Picks the catalog for [choice]; [AppLanguage.SYSTEM] resolves to a translated language or English. */
    fun use(choice: AppLanguage, systemLocale: Locale = Locale.getDefault()) {
        language = when (choice) {
            AppLanguage.SYSTEM -> AppLanguage.entries.firstOrNull { it.code != null && it.code == systemLocale.language }
                ?: AppLanguage.ENGLISH
            else -> choice
        }
    }

    /** The locale to format dates and numbers in, matching the text. */
    val locale: Locale
        get() = when (language) {
            AppLanguage.GERMAN -> Locale.GERMAN
            else -> Locale.ENGLISH
        }

    internal fun lookup(english: String): String = when (language) {
        AppLanguage.GERMAN -> German.strings[english] ?: english
        else -> english
    }

    internal fun lookupPlural(one: String, other: String): Pair<String, String> = when (language) {
        AppLanguage.GERMAN -> German.plurals[other] ?: (one to other)
        else -> one to other
    }
}

/** [english] in the current language, with `{0}`, `{1}`, … replaced by [args]. */
fun t(english: String, vararg args: Any?): String = fill(I18n.lookup(english), args)

/**
 * [english] in the current language where the same English has more than one meaning, told apart by [context]:
 * `tc("noun", "Post")` is the Pfosten, while `t("Post")` is the button that posts. Translations are keyed
 * "context:English".
 */
fun tc(context: String, english: String, vararg args: Any?): String =
    fill(I18n.lookup("$context:$english").let { if (it == "$context:$english") english else it }, args)

/**
 * The [one] or [other] form for [count], in the current language, with `{0}`, `{1}`, … replaced by [args].
 * Translations are keyed by [other]. English and German both use [one] only for exactly 1.
 */
fun tn(count: Long, one: String, other: String, vararg args: Any?): String {
    val (singular, plural) = I18n.lookupPlural(one, other)
    return fill(if (count == 1L) singular else plural, args)
}

fun tn(count: Int, one: String, other: String, vararg args: Any?): String = tn(count.toLong(), one, other, *args)

/** Replaces `{0}`, `{1}`, … with [args]; a placeholder with no matching argument, or any other brace, stays as written. */
internal fun fill(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var out: StringBuilder? = null
    var copied = 0
    var i = template.indexOf('{')
    while (i >= 0) {
        var j = i + 1
        var index = 0
        while (j < template.length && template[j] in '0'..'9' && index < 100_000_000) {
            index = index * 10 + (template[j] - '0')
            j++
        }
        if (j > i + 1 && j < template.length && template[j] == '}' && index < args.size) {
            val sb = out ?: StringBuilder(template.length + 16).also { out = it }
            sb.append(template, copied, i).append(args[index].toString())
            copied = j + 1
            i = template.indexOf('{', copied)
        } else {
            i = template.indexOf('{', i + 1)
        }
    }
    return out?.append(template, copied, template.length)?.toString() ?: template
}
