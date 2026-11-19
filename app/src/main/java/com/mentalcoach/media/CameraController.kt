package com.mentalcoach.media

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** CameraX Preview + ImageCapture with a front/back toggle. */
class CameraController(
    private val context: Context,
) {
    var front by mutableStateOf(true)
        private set
    var canFlip by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    fun bind(
        owner: LifecycleOwner,
        view: PreviewView,
    ) {
        this.owner = owner
        this.previewView = view
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                canFlip = hasFront && hasBack
                if (front && !hasFront) front = false
                if (!front && !hasBack) front = true
                rebind()
            }.onFailure {
                failed = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun rebind() {
        val p = provider ?: return
        val o = owner ?: return
        val v = previewView ?: return
        runCatching {
            p.unbindAll()
            val preview = Preview.Builder().build().also { it.surfaceProvider = v.surfaceProvider }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            p.bindToLifecycle(o, selector, preview, capture)
            imageCapture = capture
            ready = true
            failed = false
        }.onFailure {
            ready = false
            failed = true
        }
    }

    fun flip() {
        if (!canFlip) return
        front = !front
        rebind()
    }

    fun unbind() {
        runCatching { provider?.unbindAll() }
        ready = false
        imageCapture = null
        owner = null
        previewView = null
    }

    /** Captures a JPEG into [file]. Returns false on failure. */
    suspend fun capture(file: File): Boolean {
        val ic = imageCapture ?: return false
        return suspendCancellableCoroutine { cont ->
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }
}
