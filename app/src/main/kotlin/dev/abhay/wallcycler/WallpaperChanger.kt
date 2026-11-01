package dev.abhay.wallcycler

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Display
import kotlin.math.max
import kotlin.math.roundToInt

enum class Outcome { Changed, NoFolder, NoOtherImage, NoAccess, Failed }

object WallpaperChanger {

    /** Sets a random image from the chosen folder (not the current one) on home and lock screens. */
    fun changeNow(context: Context): Outcome {
        val settings = Settings(context)
        val folder = settings.folder ?: return Outcome.NoFolder
        val images = listImages(context, folder)
        val next = pickNext(images, settings.currentImage?.let(Uri::parse)) ?: return Outcome.NoOtherImage

        val bitmap = decodeForScreen(context, next)
        try {
            WallpaperManager.getInstance(context).setBitmap(
                bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
            )
        } finally {
            bitmap.recycle()
        }
        settings.currentImage = next.toString()
        return Outcome.Changed
    }

    /** Images directly inside [folder] (not in subfolders). */
    fun listImages(context: Context, folder: Uri): List<Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder, DocumentsContract.getTreeDocumentId(folder),
        )
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return buildList {
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1)?.startsWith("image/") == true) {
                        add(DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0)))
                    }
                }
            }
        }
    }

    fun folderName(context: Context, folder: Uri): String? {
        val doc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        return context.contentResolver.query(
            doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /**
     * Decodes the image no larger than needed to cover the screen, so a huge photo doesn't use
     * hundreds of MB. ImageDecoder also applies EXIF rotation.
     */
    private fun decodeForScreen(context: Context, image: Uri): Bitmap {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        val screenW = minOf(mode.physicalWidth, mode.physicalHeight)
        val screenH = maxOf(mode.physicalWidth, mode.physicalHeight)
        val source = ImageDecoder.createSource(context.contentResolver, image)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = info.size.width to info.size.height
            val scale = max(screenW.toFloat() / w, screenH.toFloat() / h)
            if (scale < 1f) {
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }
        }
    }
}
