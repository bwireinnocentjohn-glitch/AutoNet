package com.autonet.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class ReliableSpeechEngine(private val context: Context) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var tts: OfflineTts? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    fun initEngines() {
        try {
            val sttDir = File(context.filesDir, "vosk-en")
            if (!sttDir.exists()) copyAssetFolder("vosk-en", sttDir)
            model = Model(sttDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f)
        } catch (e: Exception) { e.printStackTrace() }

        try {
            val ttsDir = File(context.filesDir, "vits-piper-en_US-amy-low")
            if (!ttsDir.exists()) copyAssetFolder("vits-piper-en_US-amy-low", ttsDir)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "${ttsDir.absolutePath}/en_US-amy-low.onnx",
                        tokens = "${ttsDir.absolutePath}/tokens.txt",
                        dataDir = "${ttsDir.absolutePath}/espeak-ng-data"
                    ),
                    numThreads = 2,
                    provider = "cpu"
                )
            )
            tts = OfflineTts(config = config)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startListening(onResult: (String) -> Unit) {
        if (isRecording) return
        isRecording = true
        
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()
            val buffer = ShortArray(1600)
            var silenceCounter = 0
            val silenceThreshold = sampleRate * 2 // 2 seconds of silence
            
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    if (recognizer?.acceptWaveForm(buffer, read) == true) {
                        val result = recognizer?.result ?: "{}"
                        val json = JSONObject(result)
                        val text = json.optString("text", "")
                        if (text.isNotBlank()) {
                            withContext(Dispatchers.Main) { onResult(text) }
                            break
                        }
                    }
                    // Silence detection for long commands
                    var chunkSilence = 0
                    for (i in 0 until read) {
                        if (Math.abs(buffer[i].toInt()) < 500) chunkSilence++ else chunkSilence = 0
                    }
                    if (chunkSilence == read) silenceCounter += read else silenceCounter = 0
                    
                    if (silenceCounter > silenceThreshold) {
                        val finalResult = recognizer?.finalResult ?: "{}"
                        val json = JSONObject(finalResult)
                        val text = json.optString("text", "")
                        if (text.isNotBlank()) {
                            withContext(Dispatchers.Main) { onResult(text) }
                        }
                        break
                    }
                }
            }
            audioRecord?.stop()
            audioRecord?.release()
            isRecording = false
            recognizer?.reset()
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
    }

    fun speak(text: String) {
        tts?.let { engine ->
            try {
                // FIX: Use positional arguments for generate
                val audio = engine.generate(text, 0, 1.0f) 
                
                val shortSamples = ShortArray(audio.samples.size)
                for (i in audio.samples.indices) {
                    shortSamples[i] = (audio.samples[i] * 32767).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(engine.sampleRate()) 
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(shortSamples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(shortSamples, 0, shortSamples.size)
                track.play()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun copyAssetFolder(srcFolder: String, destFolder: File) {
        destFolder.mkdirs()
        val assets = context.assets.list(srcFolder) ?: return
        for (asset in assets) {
            val srcPath = "$srcFolder/$asset"
            val destFile = File(destFolder, asset)
            if (context.assets.list(srcPath)?.isNotEmpty() == true) {
                copyAssetFolder(srcPath, destFile)
            } else {
                context.assets.open(srcPath).use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun release() {
        stopListening()
        model?.close()
        tts?.release()
    }
}
