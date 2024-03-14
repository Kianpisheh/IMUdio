/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.imudio.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import com.example.imudio.databinding.MainActivityBinding
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val LOG_TAG = "RecordingLog"



class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager : SensorManager
    private var accSensor : Sensor?= null
    private var gyroSensor : Sensor?= null
    private var rotationSensor : Sensor?= null
    private var fileWriters = mutableMapOf<Int, FileWriter>()

    private val filenames = mapOf(
        Sensor.TYPE_LINEAR_ACCELERATION to "acc",
        Sensor.TYPE_GYROSCOPE to "gyro",
        Sensor.TYPE_ROTATION_VECTOR to "rotation"
    )



    private lateinit var mediaRecorder: MediaRecorder
    private var isRecording = false

//    private val ambientCallbackState = MyAmbientCallback()
//    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallbackState)


    private lateinit var binding: MainActivityBinding
    private var prevTime: Double = 0.0
    private var fileName: String = ""


    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)


    private var recordButton: RecordButton? = null
    private var recorder: MediaRecorder? = null

    private var player: MediaPlayer? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    internal inner class RecordButton(ctx: Context) : AppCompatButton(ctx) {

        var mStartRecording = true

        var clicker: OnClickListener = OnClickListener {
            onRecord(mStartRecording)
            text = when (mStartRecording) {
                true -> "Stop"
                false -> "Start"
            }
            mStartRecording = !mStartRecording
        }

        init {
            text = "Start"
            setOnClickListener(clicker)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)

        recordButton = RecordButton(this)

        val ll = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(recordButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0f))
        }

        setContentView(ll)


        // sensor setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)





//        lifecycle.addObserver(ambientObserver)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        // Request permission for recording audio
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun onRecord(start: Boolean) = if (start) {
        // Audio recording
        val recordingName =  "audio_" + getCurrentDateTime() + ".3gp"
        fileName = "${getExternalFilesDir(null)}/${recordingName}"

        for ((sensorType, sensorName) in filenames) {
            try {
                val sensorFileName = sensorName + "_" + getCurrentDateTime() + ".txt"
                fileWriters[sensorType] = FileWriter(File(getExternalFilesDir(null), sensorFileName), true) // Append mode
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        startAudioRecording()
        startSensorRecording()
    } else {
        stopAudioRecording()
        stopSensorRecording()
    }


    private fun startAudioRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
            start()
        }
    }

    private fun stopAudioRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    private fun startSensorRecording() {
        accSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }

    }

    private fun stopSensorRecording() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val currTime = System.currentTimeMillis()
            Log.d("freq", (1000 / (currTime - prevTime)).toString())
            prevTime = currTime.toDouble()

            val sensorType = event.sensor.type
            val timestamp = System.currentTimeMillis()

            if (sensorType in filenames) {

                val dataString = arrayToString(timestamp, event.values)
                try {
                    fileWriters[sensorType]?.write(dataString)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onResume() {
        super.onResume()
        if (recordButton?.mStartRecording == false) {
            startSensorRecording()
        }
    }

    override fun onPause() {
        super.onPause()
        if (recordButton?.mStartRecording == true) {
            stopSensorRecording()
        }
    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun arrayToString(timestamp: Long, values: FloatArray): String {
        return timestamp.toString() + "," + values.joinToString(separator = ",") + "\n"
    }

    private fun getCurrentDateTime(): String {
        // Get current date and time
        val currentDateTime = LocalDateTime.now()

        // Define a formatter for the desired output format
        val formatter = DateTimeFormatter.ofPattern("MM-dd-HH-mm-ss")

        // Format the current date and time using the formatter
        return currentDateTime.format(formatter)
    }

//    private inner class MyAmbientCallback : AmbientLifecycleObserver.AmbientLifecycleCallback {
//
//        val isAmbient: Boolean
//            get() = ambientDetails != null
//
//        var ambientDetails: AmbientLifecycleObserver.AmbientDetails? = null
//
//        /**
//         * Prepares the UI for ambient mode.
//         */
//        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
//            this.ambientDetails = ambientDetails
//
//            // Cancel any active updates
//            activeUpdateJob.cancel()
//
//            /*
//             * Following best practices outlined in WatchFaces API (keeping most pixels black,
//             * avoiding large blocks of white pixels, using only black and white, and disabling
//             * anti-aliasing, etc.)
//             */
//            binding.state.setTextColor(Color.WHITE)
//            binding.updateRate.setTextColor(Color.WHITE)
//            binding.drawCount.setTextColor(Color.WHITE)
//            if (ambientDetails.deviceHasLowBitAmbient) {
//                binding.time.paint.isAntiAlias = false
//                binding.timeStamp.paint.isAntiAlias = false
//                binding.state.paint.isAntiAlias = false
//                binding.updateRate.paint.isAntiAlias = false
//                binding.drawCount.paint.isAntiAlias = false
//            }
//            refreshDisplayAndSetNextUpdate()
//        }
//
//        /**
//         * Updates the display in ambient mode on the standard interval. Since we're using a custom
//         * refresh cycle, this method does NOT update the data in the display. Rather, this method
//         * simply updates the positioning of the data in the screen to avoid burn-in, if the display
//         * requires it.
//         */
//        override fun onUpdateAmbient() {
//            /*
//             * If the screen requires burn-in protection, views must be shifted around periodically
//             * in ambient mode. To ensure that content isn't shifted off the screen, avoid placing
//             * content within 10 pixels of the edge of the screen.
//             *
//             * Since we're potentially applying negative padding, we have ensured
//             * that the containing view is sufficiently padded (see res/layout/activity_main.xml).
//             *
//             * Activities should also avoid solid white areas to prevent pixel burn-in. Both of
//             * these requirements only apply in ambient mode, and only when this property is set
//             * to true.
//             */
//            if (ambientDetails?.burnInProtectionRequired == true) {
//                binding.container.translationX =
//                    Random.nextInt(-BURN_IN_OFFSET_PX, BURN_IN_OFFSET_PX + 1).toFloat()
//                binding.container.translationY =
//                    Random.nextInt(-BURN_IN_OFFSET_PX, BURN_IN_OFFSET_PX + 1).toFloat()
//            }
//        }
//
//        /**
//         * Restores the UI to active (non-ambient) mode.
//         */
//        override fun onExitAmbient() {
//            this.ambientDetails = null
//
//            /* Clears out Alarms since they are only used in ambient mode. */
//            ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent)
//            binding.state.setTextColor(Color.GREEN)
//            binding.updateRate.setTextColor(Color.GREEN)
//            binding.drawCount.setTextColor(Color.GREEN)
//
//            /* Reset any low bit mode. */
//            binding.time.paint.isAntiAlias = true
//            binding.timeStamp.paint.isAntiAlias = true
//            binding.state.paint.isAntiAlias = true
//            binding.updateRate.paint.isAntiAlias = true
//            binding.drawCount.paint.isAntiAlias = true
//
//            /* Reset any random offset applied for burn-in protection. */
//            binding.container.translationX = 0f
//            binding.container.translationY = 0f
//            refreshDisplayAndSetNextUpdate()
//        }
//    }
}


