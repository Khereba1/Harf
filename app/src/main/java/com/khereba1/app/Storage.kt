package com.khereba1.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** The single "current project" is kept on disk so a killed process never loses work. */
object Session {
    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "session")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun photoFile(ctx: Context): File = File(dir(ctx), "photo.src")
    private fun jsonFile(ctx: Context): File = File(dir(ctx), "project.json")

    fun save(ctx: Context, p: Project) {
        try {
            val tmp = File(dir(ctx), "project.tmp")
            tmp.writeText(projectToJson(p).toString())
            val target = jsonFile(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Save failed", e)
        }
    }

    fun load(ctx: Context): Project? {
        val f = jsonFile(ctx)
        if (!f.exists()) return null
        return try {
            projectFromJson(JSONObject(f.readText()))
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Load failed", e)
            null
        }
    }

    fun clear(ctx: Context) {
        try {
            jsonFile(ctx).delete()
            photoFile(ctx).delete()
            clearImages(ctx)
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Clear failed", e)
        }
    }

    private fun imageFile(ctx: Context, key: String): File = File(dir(ctx), "img_" + key + ".src")

    fun imageFileFor(ctx: Context, key: String): File = imageFile(ctx, key)

    fun clearImages(ctx: Context) {
        try {
            val files = dir(ctx).listFiles() ?: return
            for (f in files) if (f.name.startsWith("img_")) f.delete()
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Clearing images failed", e)
        }
    }

    /** Copies a picked picture (an image layer) into private storage under [key]. */
    fun copyImageIn(ctx: Context, uri: Uri, key: String): Boolean {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            input.use { src -> imageFile(ctx, key).outputStream().use { dst -> src.copyTo(dst) } }
            true
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Copying image failed", e)
            false
        }
    }

    /** Copies the picked image into private storage so we can reload it after a restart. */
    fun copyPhotoIn(ctx: Context, uri: Uri): Boolean {
        val tmp = File(dir(ctx), "photo.tmp")
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            val target = photoFile(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Throwable) {
            AppLog.log("SESSION", "Copying photo failed", e)
            tmp.delete()
            false
        }
    }
}

object ImageIO {
    /** Longest side we keep in memory. Keeps big photos from crashing low-RAM phones. */
    const val MAX_SIDE = 2560

    /** Decodes a photo (EXIF rotation applied) into a software bitmap, downscaled if huge. */
    fun decode(file: File, maxSide: Int = MAX_SIDE): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > maxSide) {
                    val r = maxSide.toFloat() / longest.toFloat()
                    decoder.setTargetSize(max(1, (w * r).roundToInt()), max(1, (h * r).roundToInt()))
                }
            }
        } catch (e: Throwable) {
            AppLog.log("IMAGE", "Decoding failed", e)
            null
        }
    }

    /** Saves into Pictures/Harf through MediaStore (no storage permission needed). */
    fun saveToGallery(ctx: Context, bmp: Bitmap, png: Boolean, quality: Int): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Harf_" + stamp + (if (png) ".png" else ".jpg")
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name)
        values.put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Harf")
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
            val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            out.use { stream ->
                if (!bmp.compress(format, quality, stream)) throw java.io.IOException("Compress failed")
            }
            val done = ContentValues()
            done.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
            uri
        } catch (e: Throwable) {
            AppLog.log("EXPORT", "Saving to gallery failed", e)
            try {
                resolver.delete(uri, null, null)
            } catch (e2: Throwable) {
                // ignore
            }
            null
        }
    }
}
