package com.example.arej00

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

data class DetectedAprilTag(
    val id: Int,
    val corners: List<Point>
)

data class DetectedDataMatrix(
    val value: String
)

data class CombinedDetectionResult(
    val aprilTags: List<DetectedAprilTag>,
    val dataMatrices: List<DetectedDataMatrix>
)

class CombinedBarcodeAnalyzer(
    private val onCodesDetected: (CombinedDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val aprilTagDictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_36h11)
    private val aprilTagParameters = DetectorParameters()
    private val aprilTagDetector = ArucoDetector(aprilTagDictionary, aprilTagParameters)
    private val dataMatrixScanner = BarcodeScanning.getClient()
    private val executorService = Executors.newFixedThreadPool(2)
    private var frameCount = 0

    override fun analyze(image: ImageProxy) {
        frameCount++
        if (frameCount % 7 != 0) {
            image.close()
            return
        }

        try {
            val bitmap = image.toBitmap()
            var aprilTags: List<DetectedAprilTag> = emptyList()
            var dataMatrices: List<DetectedDataMatrix> = emptyList()
            var completedDetectors = 0
            val totalDetectors = 2
            val lock = Object()

            executorService.execute {
                try {
                    aprilTags = detectAprilTags(bitmap)
                } catch (e: Exception) {
                    // Silent fail - continue with empty list
                } finally {
                    synchronized(lock) {
                        completedDetectors++
                        if (completedDetectors == totalDetectors) {
                            onCodesDetected(CombinedDetectionResult(aprilTags, dataMatrices))
                        }
                    }
                }
            }

            executorService.execute {
                try {
                    val inputImage = InputImage.fromBitmap(bitmap, image.imageInfo.rotationDegrees)

                    dataMatrixScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            dataMatrices = barcodes
                                .filter { it.format == Barcode.FORMAT_DATA_MATRIX }
                                .map { DetectedDataMatrix(it.rawValue ?: "") }
                        }
                        .addOnCompleteListener {
                            synchronized(lock) {
                                completedDetectors++
                                if (completedDetectors == totalDetectors) {
                                    onCodesDetected(CombinedDetectionResult(aprilTags, dataMatrices))
                                }
                            }
                        }
                } catch (e: Exception) {
                    synchronized(lock) {
                        completedDetectors++
                        if (completedDetectors == totalDetectors) {
                            onCodesDetected(CombinedDetectionResult(aprilTags, dataMatrices))
                        }
                    }
                }
            }

        } catch (e: Exception) {
            // Silent fail - skip this frame
        } finally {
            image.close()
        }
    }

    private fun detectAprilTags(bitmap: Bitmap): List<DetectedAprilTag> {
        val tags = mutableListOf<DetectedAprilTag>()
        val rgbaMat = Mat()
        Utils.bitmapToMat(bitmap, rgbaMat)
        val grayMat = Mat()
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        val corners = mutableListOf<Mat>()
        val ids = Mat()

        aprilTagDetector.detectMarkers(grayMat, corners, ids)

        if (ids.rows() > 0) {
            for (i in 0 until ids.rows()) {
                val id = ids.get(i, 0)[0].toInt()
                val cornerMat = corners[i]
                val tagCorners = listOf(
                    Point(cornerMat.get(0, 0)[0], cornerMat.get(0, 0)[1]),
                    Point(cornerMat.get(0, 1)[0], cornerMat.get(0, 1)[1]),
                    Point(cornerMat.get(0, 2)[0], cornerMat.get(0, 2)[1]),
                    Point(cornerMat.get(0, 3)[0], cornerMat.get(0, 3)[1])
                )
                tags.add(DetectedAprilTag(id, tagCorners))
            }
        }

        rgbaMat.release()
        grayMat.release()
        ids.release()
        corners.forEach { it.release() }
        return tags
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun cleanup() {
        executorService.shutdown()
        dataMatrixScanner.close()
    }
}