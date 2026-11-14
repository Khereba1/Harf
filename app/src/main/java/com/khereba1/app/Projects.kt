package com.khereba1.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SavedInfo(val id: String, val name: String, val modified: Long, val thumb: File?)

/**
 * Saved projects live in filesDir/projects/<id>/ using the same file names as the working
 * session (project.json, photo.src, img_<key>.src) plus meta.json and thumb.jpg.
 * A ".harf" file is simply a zip of that folder, so projects can be backed up and moved.
 */
object ProjectStore {
    private val ENTRY_OK = Regex("^(project\\.json|meta\\.json|thumb\\.jpg|photo\\.src|img_[A-Za-z0-9]+\\.src)$")
    private const val MAX_IMPORT_BYTES = 300L * 1024L * 1024L

    private fun root(ctx: Context): File {
        val d = File(ctx.filesDir, "projects")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun dirOf(ctx: Context, id: String): File = File(root(ctx), id)

    fun newId(): String = "p" + System.currentTimeMillis().toString()

    fun list(ctx: Context): List<SavedInfo> {
        val out = ArrayList<SavedInfo>()
        val dirs = root(ctx).listFiles() ?: return out
        for (d in dirs) {
            if (!d.isDirectory) continue
            try {
                val metaFile = File(d, "meta.json")
                if (!File(d, "project.json").exists()) continue
                val meta = if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
                val thumb = File(d, "thumb.jpg")
                out.add(
                    SavedInfo(
                        id = d.name,
                        name = meta.optString("name", d.name),
                        modified = meta.optLong("modified", d.lastModified()),
                        thumb = if (thumb.exists()) thumb else null,
                    ),
                )
            } catch (e: Throwable) {
                AppLog.log("PROJECTS", "Reading a saved project failed", e)
            }
        }
        out.sortByDescending { it.modified }
        return out
    }

    private fun writeMeta(dir: File, name: String) {
        val o = JSONObject()
        o.put("name", name)
        o.put("modified", System.currentTimeMillis())
        File(dir, "meta.json").writeText(o.toString())
    }

    /** Writes the open project (and every file it needs) into its folder. */
    fun save(ctx: Context, id: String, name: String, p: Project, photo: Bitmap?, images: Map<String, Bitmap>): Boolean {
        return try {
            val dir = dirOf(ctx, id)
            if (!dir.exists()) dir.mkdirs()
            // keep the old thumbnail until a new one is ready; drop stale image files
            dir.listFiles()?.forEach { if (it.name.startsWith("img_") || it.name == "photo.src") it.delete() }
            File(dir, "project.json").writeText(projectToJson(p).toString())
            if (p.hasPhoto) Session.photoFile(ctx).copyTo(File(dir, "photo.src"), overwrite = true)
            for (l in p.items) {
                if (l.type == TYPE_IMAGE && l.imageKey.isNotEmpty()) {
                    val src = Session.imageFileFor(ctx, l.imageKey)
                    if (src.exists()) src.copyTo(File(dir, "img_" + l.imageKey + ".src"), overwrite = true)
                }
            }
            val thumb = Renderer.thumbnail(p, photo, images, 360)
            if (thumb != null) {
                try {
                    FileOutputStream(File(dir, "thumb.jpg")).use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } finally {
                    thumb.recycle()
                }
            }
            writeMeta(dir, name)
            true
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Saving project failed", e)
            false
        }
    }

    /** Replaces the working session with a saved project. */
    fun openInto(ctx: Context, id: String): Boolean {
        return try {
            val dir = dirOf(ctx, id)
            if (!File(dir, "project.json").exists()) return false
            Session.clear(ctx)
            val sessionDir = File(ctx.filesDir, "session")
            if (!sessionDir.exists()) sessionDir.mkdirs()
            dir.listFiles()?.forEach { f ->
                if (f.name == "project.json" || f.name == "photo.src" || f.name.startsWith("img_")) {
                    f.copyTo(File(sessionDir, f.name), overwrite = true)
                }
            }
            true
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Opening project failed", e)
            false
        }
    }

    fun delete(ctx: Context, id: String) {
        try {
            dirOf(ctx, id).deleteRecursively()
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Deleting project failed", e)
        }
    }

    fun rename(ctx: Context, id: String, name: String) {
        try {
            val dir = dirOf(ctx, id)
            if (dir.exists()) writeMeta(dir, name)
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Renaming project failed", e)
        }
    }

    /** Zips a saved project into Downloads/Harf (through MediaStore, no permission needed). Returns the file's uri. */
    fun exportFile(ctx: Context, id: String, name: String): Uri? {
        val dir = dirOf(ctx, id)
        if (!dir.exists()) return null
        val tmp = File(ctx.cacheDir, "export.harf")
        return try {
            ZipOutputStream(FileOutputStream(tmp)).use { zip ->
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && ENTRY_OK.matches(f.name)) {
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "Harf" }
            val values = ContentValues()
            values.put(MediaStore.Downloads.DISPLAY_NAME, safe + ".harf")
            values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Harf")
            values.put(MediaStore.Downloads.IS_PENDING, 1)
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
                out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, done, null, null)
                uri
            } catch (e: Throwable) {
                try {
                    resolver.delete(uri, null, null)
                } catch (e2: Throwable) {
                    // ignore
                }
                throw e
            }
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Exporting project failed", e)
            null
        } finally {
            tmp.delete()
        }
    }

    /** Reads a .harf file into a new saved project. Only known file names are accepted. Returns the new id or null. */
    fun importFile(ctx: Context, uri: Uri): String? {
        val id = newId()
        val dir = dirOf(ctx, id)
        return try {
            dir.mkdirs()
            var total = 0L
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !ENTRY_OK.matches(entry.name)) continue
                    FileOutputStream(File(dir, entry.name)).use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_IMPORT_BYTES) throw java.io.IOException("Too large")
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            val pj = File(dir, "project.json")
            if (!pj.exists()) throw java.io.IOException("No project.json")
            val project = projectFromJson(JSONObject(pj.readText()))
            if (project.docW <= 0 || project.docH <= 0) throw java.io.IOException("Bad size")
            if (!File(dir, "meta.json").exists()) writeMeta(dir, "Harf")
            id
        } catch (e: Throwable) {
            AppLog.log("PROJECTS", "Importing project failed", e)
            dir.deleteRecursively()
            null
        }
    }
}
