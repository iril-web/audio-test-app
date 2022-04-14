package dk.iril.audiotest.service

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import dk.iril.audiotest.common.LOG_TAG
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val EMPTY_MEDIA_ROOT_ID = "empty_root_id"

class PlaybackService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaSessionCallback: MediaSessionCallback

    private val notificationHelper = NotificationHelper()

    private val scope = GlobalScope

    override fun onCreate() {
        super.onCreate()

        val mediaSession = MediaSessionCompat(this, LOG_TAG)
        mediaSessionCallback = MediaSessionCallback(context = this, mediaSession = mediaSession)

        mediaSession.apply {
            // Enable callbacks from MediaButtons and TransportControls
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_STOP
                )
            setPlaybackState(stateBuilder.build())

            setCallback(mediaSessionCallback)

            setSessionToken(sessionToken)
        }
        this.mediaSession = mediaSession

        subscribeToPlaybackEvents()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSessionCallback.release()

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.controller?.transportControls?.pause()

        super.onTaskRemoved(rootIntent)
    }

    private fun subscribeToPlaybackEvents() {
        scope.launch {
            mediaSessionCallback.isPlayingFlow.collect { isPlaying ->
                if (isPlaying) {
                    mediaSession?.let {
                        // Start foreground service. Show notification
                        val notification = notificationHelper.createNotification(this@PlaybackService, it)
                        NotificationManagerCompat.from(this@PlaybackService).notify(1, notification)
                        startForeground(1, notification)
                    }
                } else {
                    mediaSession?.let {
                        val notification = notificationHelper.createNotification(this@PlaybackService, it)

                        NotificationManagerCompat.from(this@PlaybackService).notify(1, notification)
                   }

                    stopForeground(STOP_FOREGROUND_DETACH)

                    // TODO: After 1 minute, and when there are no clients bound to the service, stop the service (stopSelf)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "PlaybackService onStartCommand " + intent?.action)

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Clients can connect, but this BrowserRoot is an empty hierarchy
        // so onLoadChildren returns nothing. This disables the ability to browse for content.
        return MediaBrowserServiceCompat.BrowserRoot(EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        //  Browsing not allowed
        if (EMPTY_MEDIA_ROOT_ID == parentId) {
            result.sendResult(null)
            return
        }
    }
}
