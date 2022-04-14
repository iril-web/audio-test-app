package dk.iril.audiotest.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import dk.iril.audiotest.common.LOG_TAG
import kotlinx.coroutines.flow.MutableStateFlow

class MediaSessionCallback(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) : MediaSessionCompat.Callback(), AudioManager.OnAudioFocusChangeListener {

    val isPlayingFlow = MutableStateFlow(false)

    private val becomingNoisyReceiver = BecomingNoisyReceiver(mediaSession = mediaSession)

    private var audioFocusRequest: AudioFocusRequest? = null

    private val player = SineWavePlayer()

    override fun onPlay() {
        super.onPlay()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setOnAudioFocusChangeListener(this@MediaSessionCallback)
            setAudioAttributes(AudioAttributes.Builder().run {
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            build()
        }
        this.audioFocusRequest = audioFocusRequest

        val result = am.requestAudioFocus(audioFocusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            context.startService(Intent(context, PlaybackService::class.java))

            // Set the session active  (and update metadata and state)
            mediaSession.isActive = true
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder().setState(
                    PlaybackStateCompat.STATE_PLAYING, 0,
                0F
            ).build())

            player.play()

            becomingNoisyReceiver.register(context)

            isPlayingFlow.value = true
        }
    }

    public override fun onPause() {
        super.onPause()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Update metadata and state
        player.pause()
        mediaSession?.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED, 0,
            0F
        ).build())

        becomingNoisyReceiver.unregister(context)

        isPlayingFlow.value = false
    }

    public override fun onStop() {
        // Abandon audio focus
        audioFocusRequest?.let {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(it)
        }

        context.unregisterReceiver(becomingNoisyReceiver)

        // Stop the service
        // TODO: service.stopSelf()

        // Set the session inactive  (and update metadata and state)
        mediaSession.isActive = false

        player.pause()

        isPlayingFlow.value = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaSession.controller.transportControls.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                mediaSession.controller.transportControls.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mediaSession.controller.transportControls.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Not handling
            }
        }
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        Log.d(LOG_TAG, "MediaSessionCallback onMediaButtonEvent " + mediaButtonEvent + " " + mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT))

        mediaButtonEvent?.let {
            if (Intent.ACTION_MEDIA_BUTTON == it.action) {
                val event = it.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

                when (event?.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                    KeyEvent.KEYCODE_MEDIA_STOP -> onStop()
                    else -> {} // Do nothing
                }
            }
        }

        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    fun release() {
        becomingNoisyReceiver.unregister(context)
    }
}

class BecomingNoisyReceiver(private val mediaSession: MediaSessionCompat) : BroadcastReceiver() {

    private val becomingNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var isRegistered = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            mediaSession.controller.transportControls.pause()
        }
    }

    fun register(context: Context) {
        if (!isRegistered) {
            context.registerReceiver(this, becomingNoisyIntentFilter)
            isRegistered = true
        }
    }

    fun unregister(context: Context) {
        if (isRegistered) {
            context.unregisterReceiver(this)
            isRegistered = false
        }
    }
}