package com.khereba1.app

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class FontEntry(val key: String, val name: String)

/**
 * Fonts come from three places:
 *  - "sys:<family>"   Android system families (always available)
 *  - "asset:fonts/x"  open-licensed fonts downloaded into assets/fonts by the GitHub workflow
 *  - "file:<path>"    fonts the user imported (copied into the app's private folder)
 */
object FontStore {
    private var appContext: Context? = null
    private val cache = HashMap<String, Typeface>()

    private val systemFonts = listOf(
        FontEntry("sys:sans-serif", "Sans"),
        FontEntry("sys:sans-serif-medium", "Sans Medium"),
        FontEntry("sys:sans-serif-black", "Sans Black"),
        FontEntry("sys:sans-serif-condensed", "Condensed"),
        FontEntry("sys:serif", "Serif"),
        FontEntry("sys:monospace", "Mono"),
        FontEntry("sys:cursive", "Cursive"),
        FontEntry("sys:casual", "Casual"),
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun fontsDir(): File? {
        val c = appContext ?: return null
        val d = File(c.filesDir, "fonts")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun isFontFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    private fun prettyName(fileName: String): String {
        return fileName.substringBeforeLast('.').replace('-', ' ').replace('_', ' ')
    }

    @Synchronized
    fun allFonts(): List<FontEntry> {
        val out = ArrayList<FontEntry>(systemFonts)
        val c = appContext
        if (c != null) {
            try {
                val names = c.assets.list("fonts")
                if (names != null) {
                    for (n in names.sorted()) {
                        if (isFontFile(n)) out.add(FontEntry("asset:fonts/" + n, prettyName(n)))
                    }
                }
            } catch (e: Throwable) {
                AppLog.log("FONT", "Listing bundled fonts failed", e)
            }
        }
        val files = fontsDir()?.listFiles()
        if (files != null) {
            for (f in files.sortedBy { it.name }) {
                if (isFontFile(f.name)) out.add(FontEntry("file:" + f.absolutePath, prettyName(f.name)))
            }
        }
        return out
    }

    @Synchronized
    private fun baseTypeface(key: String): Typeface {
        val c = appContext
        return try {
            when {
                key.startsWith("sys:") -> Typeface.create(key.removePrefix("sys:"), Typeface.NORMAL)
                key.startsWith("asset:") && c != null -> Typeface.createFromAsset(c.assets, key.removePrefix("asset:"))
                key.startsWith("file:") -> Typeface.createFromFile(key.removePrefix("file:"))
                else -> Typeface.DEFAULT
            }
        } catch (e: Throwable) {
            AppLog.log("FONT", "Could not load font " + key, e)
            Typeface.DEFAULT
        }
    }

    @Synchronized
    fun typeface(key: String, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val cacheKey = key + "|" + style
        val hit = cache[cacheKey]
        if (hit != null) return hit
        val tf = Typeface.create(baseTypeface(key), style)
        cache[cacheKey] = tf
        return tf
    }

    /** Copies a user-picked .ttf/.otf into the app folder. Returns null if it could not be used. */
    @Synchronized
    fun importFont(uri: Uri): FontEntry? {
        val c = appContext ?: return null
        val dir = fontsDir() ?: return null
        var shown: String? = null
        try {
            val cursor = c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null) {
                cursor.use {
                    if (it.moveToFirst()) shown = it.getString(0)
                }
            }
        } catch (e: Throwable) {
            AppLog.log("FONT", "Could not read font name", e)
        }
        var name = (shown ?: "font").replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (!isFontFile(name)) name = name + ".ttf"
        val target = File(dir, name)
        return try {
            val input = c.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
            Typeface.createFromFile(target)
            cache.clear()
            FontEntry("file:" + target.absolutePath, prettyName(name))
        } catch (e: Throwable) {
            AppLog.log("FONT", "Font import failed", e)
            target.delete()
            null
        }
    }
}
