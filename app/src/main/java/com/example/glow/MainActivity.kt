package com.example.glow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.glow.data.CheckInDatabase
import com.example.glow.data.CheckInEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.withContext
@android.annotation.SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnStartCheckIn: Button
    private lateinit var btnViewHistory: Button
    private lateinit var rootLayout: ConstraintLayout

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private var isCheckingIn = false
    private var originalBrightness = 0.0f

    private lateinit var tts: TextToSpeech // Voice Assistant

    private enum class CheckInPhase { BASELINE, STIMULATION, RECOVERY }
    private var currentPhase = CheckInPhase.BASELINE

    private val baselineData = Collections.synchronizedList(mutableListOf<Float>())
    private val stimulationData = Collections.synchronizedList(mutableListOf<Float>())
    private val recoveryData = Collections.synchronizedList(mutableListOf<Float>())

    private val db by lazy { CheckInDatabase.getDatabase(this) }
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        tvStatus = findViewById(R.id.tvStatus)
        btnStartCheckIn = findViewById(R.id.btnStartCheckIn)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        rootLayout = findViewById(R.id.rootLayout)

        window.attributes = window.attributes.apply {
            originalBrightness = screenBrightness
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize the Glow AI Voice
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        btnStartCheckIn.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
                return@setOnClickListener
            }

            if (!isCheckingIn) {
                startCheckInSequence()
            }
        }

        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCheckInSequence() {
        isCheckingIn = true
        btnStartCheckIn.isEnabled = false
        btnViewHistory.isEnabled = false

        // Show the premium loader, hide the face guide
        findViewById<View>(R.id.faceGuideOverlay).visibility = View.GONE
        val progressLoader = findViewById<View>(R.id.progressLoader)
        progressLoader.visibility = View.VISIBLE

        baselineData.clear()
        stimulationData.clear()
        recoveryData.clear()

        object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000

                when {
                    secondsLeft > 11 -> {
                        currentPhase = CheckInPhase.BASELINE
                        tvStatus.text = String.format(Locale.getDefault(), "Baseline... %ds", secondsLeft)
                        turnScreenFlashOff()
                        if (secondsLeft == 14L) speak("Initiating baseline. Hold still.")
                    }
                    secondsLeft > 6 -> {
                        currentPhase = CheckInPhase.STIMULATION
                        tvStatus.text = String.format(Locale.getDefault(), "Measuring... %ds", secondsLeft)
                        turnScreenFlashOn()
                        if (secondsLeft == 10L) speak("Measuring pupil response.")
                    }
                    else -> {
                        currentPhase = CheckInPhase.RECOVERY
                        tvStatus.text = String.format(Locale.getDefault(), "Recovering... %ds", secondsLeft)
                        turnScreenFlashOff()
                        if (secondsLeft == 6L) speak("Analyzing recovery.")
                    }
                }
            }

            override fun onFinish() {
                turnScreenFlashOff()
                isCheckingIn = false
                btnStartCheckIn.isEnabled = true
                btnViewHistory.isEnabled = true

                progressLoader.visibility = View.GONE
                findViewById<View>(R.id.faceGuideOverlay).visibility = View.VISIBLE

                calculateFatigueScore()
            }
        }.start()
    }

    private fun calculateFatigueScore() {
        val baselineAvg = if (baselineData.isNotEmpty()) baselineData.average().toFloat() else 0.9f
        val recoveryAvg = if (recoveryData.isNotEmpty()) recoveryData.average().toFloat() else 0.85f

        val recoveryRatio = recoveryAvg / baselineAvg
        val responseTimeDouble = 1.1 - recoveryRatio
        val responseTime = String.format(Locale.getDefault(), "%.2f", responseTimeDouble)

        val state: String
        val stateEmoji: String

        when {
            recoveryRatio >= 0.90 -> {
                state = "RESTED"
                stateEmoji = "🟢"
            }
            recoveryRatio >= 0.80 -> {
                state = "FATIGUED"
                stateEmoji = "🟡"
            }
            else -> {
                state = "STRESSED"
                stateEmoji = "🔴"
            }
        }

        showResultDialog(stateEmoji, state, responseTimeDouble, responseTime)
    }

    private fun showResultDialog(emoji: String, state: String, rawResponseTime: Double, formattedResponseTime: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_result, null)

        val dialogRoot = dialogView.findViewById<View>(R.id.dialogRoot)
        val tvResultEmoji = dialogView.findViewById<TextView>(R.id.tvResultEmoji)
        val tvResultState = dialogView.findViewById<TextView>(R.id.tvResultState)
        val tvResultTime = dialogView.findViewById<TextView>(R.id.tvResultTime)
        val tvResultTip = dialogView.findViewById<TextView>(R.id.tvResultTip)

        tvResultEmoji.text = emoji
        tvResultState.text = state
        tvResultTime.text = String.format(Locale.getDefault(), "Response time: %ss\n(Normal: 0.30-0.40s)", formattedResponseTime)

        // Smart Health Tips & Color Coding
        when (state) {
            "RESTED" -> {
                dialogRoot.setBackgroundResource(R.color.result_green_bg)
                tvResultTip.text = "💡 Tip: You are in the zone! Maintain your momentum and tackle complex tasks now."
                speak("Analysis complete. You are rested. You are in the zone.")
            }
            "FATIGUED" -> {
                dialogRoot.setBackgroundResource(R.color.result_yellow_bg)
                tvResultTip.text = "💡 Tip: A 20-minute power nap or a short walk can significantly restore cognitive function."
                speak("Analysis complete. You are fatigued. Consider taking a short break.")
            }
            "STRESSED" -> {
                dialogRoot.setBackgroundResource(R.color.result_red_bg)
                tvResultTip.text = "💡 Tip: Try the 4-7-8 breathing technique. Inhale for 4s, hold for 7s, exhale for 8s."
                speak("Warning. You are stressed. Please take a rest and breathe.")
            }
        }

        // Premium Pop-in Animation for the Emoji
        tvResultEmoji.alpha = 0f
        tvResultEmoji.scaleX = 0.5f
        tvResultEmoji.scaleY = 0.5f
        tvResultEmoji.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(300).withEndAction {
            tvResultEmoji.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        }.start()

        builder.setView(dialogView)
            .setPositiveButton("Save & Start Activities") { dialog, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val newCheckIn = CheckInEntity(
                        timestamp = System.currentTimeMillis(),
                        state = state,
                        responseTime = rawResponseTime
                    )

                    // Just insert it, don't worry about the return ID
                    db.checkInDao().insertCheckIn(newCheckIn)

                    // Launch Recommendation Activity with ONLY the State
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, RecommendationActivity::class.java)
                        intent.putExtra("STATE", state) // We only pass the state now!
                        startActivity(intent)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Discard") { dialog, _ ->
                tvStatus.text = "Face Detected ✓\nTap Start"
                dialog.dismiss()
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()
    }

    private fun turnScreenFlashOn() {
        window.attributes = window.attributes.apply { screenBrightness = 1.0f }
        rootLayout.setBackgroundColor(Color.WHITE)
        tvStatus.setTextColor(Color.BLACK)
        btnStartCheckIn.setTextColor(Color.BLACK)
        btnViewHistory.setTextColor(Color.BLACK)
    }

    private fun turnScreenFlashOff() {
        window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
        rootLayout.setBackgroundColor(Color.BLACK)
        tvStatus.setTextColor(Color.WHITE)
        btnStartCheckIn.setTextColor(Color.WHITE)
        btnViewHistory.setTextColor(Color.WHITE)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val detectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.5f)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()

            val detector = FaceDetection.getClient(detectorOptions)

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImageProxy(detector, imageProxy)
                    })
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(
        detector: com.google.mlkit.vision.face.FaceDetector,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val faceGuideOverlay = findViewById<View>(R.id.faceGuideOverlay)
                    if (faces.isNotEmpty()) {
                        val face = faces[0]

                        if (isCheckingIn) {
                            val leftEyeProb = face.leftEyeOpenProbability ?: 0.9f
                            val rightEyeProb = face.rightEyeOpenProbability ?: 0.9f
                            val avgEyeProb = (leftEyeProb + rightEyeProb) / 2.0f

                            when (currentPhase) {
                                CheckInPhase.BASELINE -> baselineData.add(avgEyeProb)
                                CheckInPhase.STIMULATION -> stimulationData.add(avgEyeProb)
                                CheckInPhase.RECOVERY -> recoveryData.add(avgEyeProb)
                            }
                        } else {
                            runOnUiThread {
                                tvStatus.text = "Face Detected ✓\nTap Start"
                                speak("Face recognized. Ready for check in.")
                                faceGuideOverlay.setBackgroundResource(R.drawable.oval_guide_active)
                                faceGuideOverlay.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start()
                            }
                        }
                    } else if (!isCheckingIn) {
                        runOnUiThread {
                            tvStatus.text = "Align your face in the oval"
                            faceGuideOverlay.setBackgroundResource(R.drawable.oval_guide)
                            faceGuideOverlay.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    // --- VOICE ASSISTANT & LIFECYCLE ---

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GLOW_AI")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    companion object {
        private const val TAG = "GlowApp"
        private const val CAMERA_REQUEST_CODE = 100
    }
}