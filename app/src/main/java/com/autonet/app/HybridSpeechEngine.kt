package com.autonet.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.cactus.CactusSTT
import com.cactus.CactusContextInitializer
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
        // 1. Init Whistle (STT)
        CactusContextInitializer.initialize(context)
        val sttPath = copyAsset("models/whistle/whistle.cact")
        stt = CactusSTT().apply { loadModel(sttPath) }

        // 2. Init KittenTTS
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
        tts = OfflineTts(config)
    }

    fun startListening(onResult: (String) -> Unit) {
        if (isRecording) return
        isRecording = true
        
        // Note: Whistle uses its own audio pipeline, but for simplicity we use AudioRecord here
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()
            val buffer = ShortArray(1600)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Feed to Whistle (assuming CactusSTT has a process method)
                    // For now, we simulate the callback to keep it stable
                    val text = "simulated transcription" 
                    withContext(Dispatchers.Main) { onResult(text) }
                }
            }
            audioRecord?.stop()
            audioRecord?.release()
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
    }

    fun speak(text: String) {
        tts?.let { engine ->
            val audio = engine.generate(text, speakerId = 0, speed = 1.0f) 
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
                        .setSampleRate(engine.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(audio.samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(audio.samples, 0, audio.samples.size)
            track.play()
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
        stt?.unload()
        tts?.release()
    }
}
