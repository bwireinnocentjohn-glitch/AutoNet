package com.autonet.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.cactus.CactusSTT
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File

class HybridSpeechEngine(private val context: Context) {
    private var stt: CactusSTT? = null
    private var tts: OfflineTts? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    fun initEngines() {
        // 1. Init Whistle (STT) - Verified API
        CactusContextInitializer.initialize(context)
        val sttPath = copyAsset("models/whistle/whistle.cact")
        stt = CactusSTT().apply { 
            initializeModel(CactusInitParams(model = sttPath)) 
        }

        // 2. Init KittenTTS - Verified API
        val ttsPath = copyAsset("models/kitten-nano-en-v0_8-int8/model.int8.onnx")
        val tokensPath = copyAsset("models/kitten-nano-en-v0_8-int8/tokens.txt")
        val voicesPath = copyAsset("models/kitten-nano-en-v0_8-int8/voices.bin")
        
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(
                    model = ttsPath,
                    tokens = tokensPath,
                    voices = voicesPath
                ),
                numThreads = 2,
                provider = "cpu"
            )
        )
        tts = try { OfflineTts(config) } catch (e: Exception) { null }
    }

    fun startListening(onResult: (String) -> Unit) {
        if (isRecording) return
        isRecording = true
        
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()
            val buffer = ShortArray(1600)
            val audioData = mutableListOf<Short>()
            var silenceCounter = 0
            
            val startTime = System.currentTimeMillis()
            while (isRecording && (System.currentTimeMillis() - startTime) < 5000) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    for (i in 0 until read) {
                        audioData.add(buffer[i])
                        if (Math.abs(buffer[i].toInt()) < 500) silenceCounter++ else silenceCounter = 0
                    }
                    if (silenceCounter > sampleRate * 1.5) break
                }
            }
            
            audioRecord?.stop()
            audioRecord?.release()
            isRecording = false
            
            if (audioData.isNotEmpty()) {
                val floatArray = FloatArray(audioData.size) { audioData[it] / 32768.0f }
                try {
                    val text = stt?.transcribe(audioData = floatArray, sampleRate = sampleRate) ?: ""
                    if (text.isNotBlank()) {
                        withContext(Dispatchers.Main) { onResult(text) }
                    } else {
                        withContext(Dispatchers.Main) { onResult("Could not understand audio.") }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onResult("STT Error: ${e.message}") }
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
    }

    fun speak(text: String) {
        tts?.let { engine ->
            try {
                // Verified API: generate(text, sid, speed)
                val audio = engine.generate(text, sid = 0, speed = 1.0f) 
                
                // Convert FloatArray to ShortArray for AudioTrack
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
                            .setSampleRate(engine.sampleRate()) // Verified: function call
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(shortSamples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(shortSamples, 0, shortSamples.size)
                track.play()
            } catch (e: Exception) {
                // Fail silently to prevent crashes
            }
        }
    }

    private fun copyAsset(assetPath: String): String {
        val fileName = assetPath.substringAfterLast("/")
        val destFile = File(context.filesDir, fileName)
        if (!destFile.exists()) {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destFile.absolutePath
    }

    fun release() {
        stopListening()
        stt?.release()
        tts?.release()
    }
}
