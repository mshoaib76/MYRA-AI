package com.myra.assistant.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        const val MIC_SAMPLE_RATE = 16000
        const val SPEAKER_SAMPLE_RATE = 24000
        const val CHUNK_SIZE = 1024
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var currentPlaybackRate = SPEAKER_SAMPLE_RATE

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)

    var onMicChunk: ((ByteArray) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null

    var suppressMicWhileSpeaking = true
    var onRecordError: ((String) -> Unit)? = null

    fun startRecording(): Boolean {
        if (isRecording.get()) return true
        if (isRecording.getAndSet(true)) return true
        val minBuf = AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufferSize = maxOf(minBuf, CHUNK_SIZE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MIC_SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed state=${audioRecord?.state}")
            isRecording.set(false)
            onRecordError?.invoke("Microphone failed — check RECORD_AUDIO permission")
            return false
        }

        try {
            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            Log.e(TAG, "Mic permission denied", e)
            isRecording.set(false)
            onRecordError?.invoke("Microphone permission denied")
            return false
        }
        recordJob = scope.launch {
            val buffer = ByteArray(CHUNK_SIZE)
            while (isActive && isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    val rms = calculateRms(chunk)
                    onAmplitudeChanged?.invoke(rms)
                    if (!muted.get() && !(suppressMicWhileSpeaking && isSpeaking.get())) {
                        onMicChunk?.invoke(chunk)
                    }
                }
            }
        }
        return true
    }

    fun startPlayback(): Boolean {
        if (isPlaying.get()) return true
        if (isPlaying.getAndSet(true)) return true
        val minBuf = AudioTrack.getMinBufferSize(currentPlaybackRate, CHANNEL_OUT, ENCODING)
        val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(currentPlaybackRate)
                    .setEncoding(ENCODING)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        playbackJob = scope.launch {
            while (isActive && isPlaying.get()) {
                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    if (!isSpeaking.getAndSet(true)) {
                        onSpeakingStarted?.invoke()
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isSpeaking.getAndSet(false)) {
                        onSpeakingStopped?.invoke()
                    }
                    kotlinx.coroutines.delay(20)
                }
            }
        }
        return true
    }

    fun queueAudio(pcmBytes: ByteArray) {
        playbackQueue.offer(pcmBytes)
    }

    fun setPlaybackSampleRate(sampleRate: Int) {
        val normalized = sampleRate.coerceIn(8_000, 48_000)
        if (normalized == currentPlaybackRate) return
        currentPlaybackRate = normalized
        if (isPlaying.get()) {
            playbackJob?.cancel()
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) { }
            audioTrack = null
            isPlaying.set(false)
            startPlayback()
        }
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        if (isSpeaking.getAndSet(false)) {
            onSpeakingStopped?.invoke()
        }
    }

    fun setMuted(mute: Boolean) {
        muted.set(mute)
    }

    fun release() {
        isRecording.set(false)
        isPlaying.set(false)
        recordJob?.cancel()
        playbackJob?.cancel()
        scope.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null

        playbackQueue.clear()
    }

    private fun calculateRms(pcm: ByteArray): Float {
        if (pcm.size < 2) return 0f
        var sum = 0.0
        var i = 0
        while (i < pcm.size - 1) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val s = sample.toShort().toInt()
            sum += s * s
            i += 2
        }
        val samples = pcm.size / 2
        val rms = kotlin.math.sqrt(sum / samples)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
