package com.huestone.shuddercam.camera

import android.content.Context
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-aware camera binding. Manages a CameraX Preview use case
 * bound to the rear camera by default. Handles clean bind/unbind
 * on lifecycle transitions and screen rotation.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private var preview: Preview? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.unbindAll()

            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview!!
            )

            cameraControl = camera.cameraControl
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraControl = null
        preview = null
    }

    /**
     * Tap-to-focus: creates a metering point from the given coordinates
     * and starts a focus/metering action.
     */
    fun tapToFocus(factory: MeteringPointFactory, x: Float, y: Float) {
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        cameraControl?.startFocusAndMetering(action)
    }
}
