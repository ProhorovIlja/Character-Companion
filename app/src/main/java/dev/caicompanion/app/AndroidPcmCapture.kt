package dev.caicompanion.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException

/** Foreground capture primitive. Caller owns lifecycle and must cancel on leaving the UI. */
internal object AndroidPcmCapture {
    data class Recording(val samples: ShortArray, val sourceRate: Int, val reachedLimit: Boolean)
    private val rates = intArrayOf(16000, 48000, 44100)

    @SuppressLint("MissingPermission")
    private fun open(rate: Int): AudioRecord? {
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0 || minimum > 1024 * 1024) return null
        var record: AudioRecord? = null
        var ready = false
        try {
            val candidate = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum, rate / 5)).build()
            record = candidate
            if (candidate.state != AudioRecord.STATE_INITIALIZED || candidate.sampleRate != rate ||
                candidate.channelCount != 1 || candidate.audioFormat != AudioFormat.ENCODING_PCM_16BIT) return null
            candidate.startRecording()
            if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) return null
            ready = true
            return candidate
        } catch (_: IllegalArgumentException) { return null }
          catch (_: IllegalStateException) { return null }
          catch (_: UnsupportedOperationException) { return null }
        finally { if (!ready) record?.release() }
    }

    /** Run only on a worker for an explicit microphone action. No recognizer, network or disk writes. */
    fun record(context: Context, maxSeconds: Int = 180, stopRequested: () -> Boolean,
               cancelled: () -> Boolean): Recording {
        require(maxSeconds in 1..180)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Microphone permission required")
        fun checkActive() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Recording cancelled")
        }
        var record: AudioRecord? = null
        var result: Recording? = null
        try {
            for (rate in rates) {
                checkActive()
                if (stopRequested()) throw IOException("Empty recording")
                record = open(rate)
                if (record != null) break
            }
            val mic = record ?: throw IOException("No supported microphone capture format")
            val rate = mic.sampleRate
            val chunk = ShortArray(rate / 50)
            try {
                PcmSpeechBuffer(rate, maxSeconds).use { audio ->
                    while (audio.remainingFrames > 0) {
                        checkActive()
                        if (stopRequested()) break
                        val requested = minOf(chunk.size, audio.remainingFrames)
                        val count = mic.read(chunk, 0, requested, AudioRecord.READ_BLOCKING)
                        checkActive()
                        if (count !in 1..requested) throw IOException("Microphone read failed")
                        audio.append(chunk, count)
                    }
                    checkActive()
                    val capped = audio.remainingFrames == 0
                    result = Recording(audio.finish(), rate, capped)
                }
            } finally { chunk.fill(0) }
            checkActive()
            return checkNotNull(result)
        } catch (failure: Throwable) {
            result?.samples?.fill(0)
            throw failure
        } finally {
            // Stop and release even after cancellation or driver errors.
            record?.let { mic ->
                try {
                    if (mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) mic.stop()
                } catch (_: IllegalStateException) {
                    // Already stopped by the audio system.
                } finally {
                    try { mic.release() }
                    catch (failure: Throwable) { result?.samples?.fill(0); throw failure }
                }
            }
        }
    }
}
