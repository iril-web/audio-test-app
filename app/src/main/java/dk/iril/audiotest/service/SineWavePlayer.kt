package dk.iril.audiotest.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

private const val minBufferSize = 1024
private const val frequency = 1000
private const val sampleRate = 44100
private const val bitsPerChannel = 16
private const val bytesPerChannel = bitsPerChannel / 8
private const val channelCount = 1
private const val bytesPerSample = channelCount * bytesPerChannel
private const val bytesPerRotation = (sampleRate * bytesPerSample * (1.0 / frequency.toDouble())).toInt()

class SineWavePlayer {

    var audioTrack: AudioTrack? = null
    var playerThread: Thread? = null

    fun play() {
        if (playerThread == null) {
            val buffer = ShortArray(minBufferSize)

            val attributes = AudioAttributes.Builder().apply {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            }.build()

            val audioFormat = AudioFormat.Builder().apply {
                setSampleRate(sampleRate)
                setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            }.build()

            val audioTrack = AudioTrack.Builder().apply {
                setAudioAttributes(attributes)
                setAudioFormat(audioFormat)
                setBufferSizeInBytes(minBufferSize)
                setTransferMode(AudioTrack.MODE_STREAM)
            }.build()
            this.audioTrack = audioTrack

            // Algorithm for sine wave originally from https://stackoverflow.com/questions/11436472/android-sine-wave-generation
            val increment =
                (2 * Math.PI).toFloat() * frequency / sampleRate // angular increment for each sample
            var angle = 0f
            val samples = FloatArray(minBufferSize)
            audioTrack.play()

            val playerThread = Thread {
                while (!Thread.currentThread().isInterrupted()) {
                    for (i in samples.indices) {
                        samples[i] =
                            Math.sin(angle.toDouble())
                                .toFloat() //the part that makes this a sine wave....
                        buffer[i] = (samples[i] * Short.MAX_VALUE).toInt().toShort()
                        angle += increment
                    }
                    audioTrack.write(
                        buffer,
                        0,
                        samples.size
                    ) //write to the audio buffer.... and start all over again!
                }
                playerThread = null
            }
            this.playerThread = playerThread
            playerThread.start()
        }
    }

    fun pause() {
        playerThread?.interrupt()
        audioTrack?.pause()
    }
}

/*
W/.iril.audiotes: Accessing hidden field Landroid/service/media/MediaBrowserService$Result;->mFlags:I (light greylist, reflection)
W/MediaSessionCompat: Couldn't find a unique registered media button receiver in the given context.
W/MediaButtonReceiver: A unique media button receiver could not be found in the given context, so couldn't build a pending intent.
W/MediaButtonReceiver: A unique media button receiver could not be found in the given context, so couldn't build a pending intent.
W/MediaButtonReceiver: A unique media button receiver could not be found in the given context, so couldn't build a pending intent.
W/.iril.audiotes: Accessing hidden method Landroid/media/session/MediaSession;->getCallingPackage()Ljava/lang/String; (light greylist, reflection)
W/.iril.audiotes: Accessing hidden method Landroid/os/Trace;->asyncTraceBegin(JLjava/lang/String;I)V (light greylist, reflection)
W/.iril.audiotes: Accessing hidden method Landroid/os/Trace;->asyncTraceEnd(JLjava/lang/String;I)V (light greylist, reflection)
W/.iril.audiotes: Accessing hidden method Landroid/os/Trace;->traceCounter(JLjava/lang/String;I)V (light greylist, reflection)
*/
