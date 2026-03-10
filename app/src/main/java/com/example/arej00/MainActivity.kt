package com.example.arej00

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var tvResult: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var combinedAnalyzer: CombinedBarcodeAnalyzer? = null
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.viewFinder)
        tvResult = findViewById(R.id.tvResult)

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(800, 480))
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            combinedAnalyzer = CombinedBarcodeAnalyzer { result ->
                runOnUiThread { displayResults(result) }
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setImageQueueDepth(1)
                .build()
                .also { it.setAnalyzer(cameraExecutor, combinedAnalyzer!!) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                enableTapToFocus(camera)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun enableTapToFocus(camera: Camera) {
        previewView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera.cameraControl.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun displayResults(result: CombinedDetectionResult) {
        if (result.aprilTags.isEmpty() && result.dataMatrices.isEmpty()) {
            tvResult.text = "Scanning"
            return
        }

        val displayText = buildString {
            if (result.aprilTags.isNotEmpty()) {
                appendLine("APRILTAGS: ${result.aprilTags.size}")
                result.aprilTags.forEachIndexed { index, tag ->
                    appendLine("Tag ${index + 1}: ID ${tag.id}")
                    appendLine("  Corners:")
                    appendLine("    TL: (${tag.corners[0].x.toInt()}, ${tag.corners[0].y.toInt()})")
                    appendLine("    TR: (${tag.corners[1].x.toInt()}, ${tag.corners[1].y.toInt()})")
                    appendLine("    BR: (${tag.corners[2].x.toInt()}, ${tag.corners[2].y.toInt()})")
                    appendLine("    BL: (${tag.corners[3].x.toInt()}, ${tag.corners[3].y.toInt()})")

                    if (index < result.aprilTags.size - 1) {
                        appendLine()
                    }
                }
            }

            if (result.dataMatrices.isNotEmpty()) {
                if (result.aprilTags.isNotEmpty()) appendLine()
                appendLine("DATA MATRIX: ${result.dataMatrices.size}")
                result.dataMatrices.forEachIndexed { index, dm ->
                    appendLine("Code ${index + 1}: ${dm.value}")
                }
            }
        }

        tvResult.text = displayText
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        combinedAnalyzer?.cleanup()
        cameraExecutor.shutdown()
    }
}
