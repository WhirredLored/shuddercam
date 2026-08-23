package com.huestone.shuddercam.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Still capture on top of CameraX ImageCapture.
 *
 * Manual settings are NOT re-applied here: ControlApplier remains the one
 * writer (Law 4), and the Camera2 interop options it sets on the shared
 * camera control ride along with the still capture request, so a photo is
 * taken with whatever the preview is currently showing.
 */
object CaptureController {

    private const val TAG = "CaptureController"

    /** Pictures/ShudderCam — the OS gallery indexes it; the app has no gallery of its own. */
    private const val ALBUM_DIR = "ShudderCam"

    private const val FILENAME_PATTERN = "'SC_'yyyyMMdd_HHmmss'.jpg'"

    /** Result of a completed capture. */
    sealed class Result {
        data class Saved(val uri: Uri) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Builds the filename for a capture taken at [date], e.g. SC_20260823_142530.jpg.
     */
    fun fileNameFor(date: Date): String =
        SimpleDateFormat(FILENAME_PATTERN, Locale.US).format(date)

    /**
     * Takes a photo and writes it to MediaStore under Pictures/ShudderCam.
     * [onResult] is delivered on the main thread.
     */
    fun capture(
        context: Context,
        imageCapture: ImageCapture,
        onResult: (Result) -> Unit
    ) {
        val name = fileNameFor(Date())

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIR"
                )
            } else {
                // Pre-Q has no RELATIVE_PATH: place the file by absolute path and
                // let MediaStore index it there.
                @Suppress("DEPRECATION")
                val albumDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM_DIR
                )
                if (!albumDir.exists()) albumDir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(albumDir, name).absolutePath)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri == null) {
                        Log.w(TAG, "Capture saved but returned no URI")
                        onResult(Result.Failed("Saved without a URI"))
                    } else {
                        Log.i(TAG, "Captured $name -> $uri")
                        onResult(Result.Saved(uri))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed: ${exception.message}", exception)
                    onResult(Result.Failed(exception.message ?: "Capture failed"))
                }
            }
        )
    }
}
