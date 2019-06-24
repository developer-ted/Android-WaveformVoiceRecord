package com.ted.waveform.waveform.player

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ted.waveform.waveform.file.SoundFile
import java.nio.ShortBuffer

class SamplePlayer {
    interface OnCompletionListener {
        fun onCompletion()
    };

    private lateinit var mSamples: ShortBuffer
    private var mSampleRate: Int = 0
    private var mChannels: Int = 0
    private var mNumSamples: Int  = 0 // Number of samples per channel.
    private lateinit var mAudioTrack: AudioTrack
    private lateinit var mBuffer: ShortArray
    private var mPlaybackStart: Int = 0  // Start offset, in samples.
    private var mPlayThread: Thread? = null
    private var mKeepPlaying: Boolean = false
    private var mListener: OnCompletionListener? = null


    fun constructor(samples: ShortBuffer, sampleRate: Int, channels: Int, numSamples: Int) {
        mSamples = samples
        mSampleRate = sampleRate
        mChannels = channels
        mNumSamples = numSamples
        mPlaybackStart = 0

        var bufferSize = AudioTrack.getMinBufferSize(
            mSampleRate,
            if (mChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // make sure minBufferSize can contain at least 1 second of audio (16 bits sample).
        if (bufferSize < mChannels * mSampleRate * 2) {
            bufferSize = mChannels * mSampleRate * 2
        }
        mBuffer = ShortArray(bufferSize / 2) // bufferSize is in Bytes.
        mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            mSampleRate,
            if (mChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            mBuffer.size * 2,
            AudioTrack.MODE_STREAM
        )
        // Check when player played all the given data and notify user if mListener is set.
        mAudioTrack.notificationMarkerPosition = mNumSamples - 1  // Set the marker to the end.
        mAudioTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onPeriodicNotification(track: AudioTrack) {}

                override fun onMarkerReached(track: AudioTrack) {
                    stop()
                    if (mListener != null) {
                        mListener!!.onCompletion()
                    }
                }
            })
        mPlayThread = null
        mKeepPlaying = true
        mListener = null
    }

    fun constructor(sf: SoundFile) {
        constructor(sf.samples, sf.sampleRate, sf.channels, sf.numSamples)
    }

    fun setOnCompletionListener(listener: OnCompletionListener) {
        mListener = listener
    }

    fun isPlaying(): Boolean {
        return mAudioTrack.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    fun isPaused(): Boolean {
        return mAudioTrack.playState == AudioTrack.PLAYSTATE_PAUSED
    }

    fun start() {
        if (isPlaying()) {
            return
        }
        mKeepPlaying = true
        mAudioTrack.flush()
        mAudioTrack.play()
        // Setting thread feeding the audio samples to the audio hardware.
        // (Assumes mChannels = 1 or 2).
        mPlayThread = object : Thread() {
            override fun run() {
                val position = mPlaybackStart * mChannels
                mSamples.position(position)
                val limit = mNumSamples * mChannels
                while (mSamples.position() < limit && mKeepPlaying) {
                    val numSamplesLeft = limit - mSamples.position()
                    if (numSamplesLeft >= mBuffer.size) {
                        mSamples.get(mBuffer)
                    } else {
                        for (i in numSamplesLeft until mBuffer.size) {
                            mBuffer[i] = 0
                        }
                        mSamples.get(mBuffer, 0, numSamplesLeft)
                    }
                    // TODO(nfaralli): use the write method that takes a ByteBuffer as argument.
                    mAudioTrack.write(mBuffer, 0, mBuffer.size)
                }
            }
        }
        mPlayThread!!.start()
    }

    fun pause() {
        if (isPlaying()) {
            mAudioTrack.pause()
            // mAudioTrack.write() should block if it cannot write.
        }
    }

    fun stop() {
        if (isPlaying() || isPaused()) {
            mKeepPlaying = false
            mAudioTrack.pause()  // pause() stops the playback immediately.
            mAudioTrack.stop()   // Unblock mAudioTrack.write() to avoid deadlocks.
            if (mPlayThread != null) {
                try {
                    mPlayThread!!.join()
                } catch (e: InterruptedException) {
                }

                mPlayThread = null
            }
            mAudioTrack.flush()  // just in case...
        }
    }

    fun release() {
        stop()
        mAudioTrack.release()
    }

    fun seekTo(msec: Int) {
        val wasPlaying = isPlaying()
        stop()
        mPlaybackStart = (msec * (mSampleRate / 1000.0)).toInt()
        if (mPlaybackStart > mNumSamples) {
            mPlaybackStart = mNumSamples  // Nothing to play...
        }
        mAudioTrack.notificationMarkerPosition = mNumSamples - 1 - mPlaybackStart
        if (wasPlaying) {
            start()
        }
    }

    fun getCurrentPosition(): Int {
        return ((mPlaybackStart + mAudioTrack.playbackHeadPosition) * (1000.0 / mSampleRate)).toInt()
    }
}