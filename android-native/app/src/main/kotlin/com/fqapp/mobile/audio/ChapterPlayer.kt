package com.fqapp.mobile.audio

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 听书播放器。用系统 MediaPlayer 而不是 ExoPlayer：这里只需要「播一个 http 音频」，
 * 断点续播与解码交给系统即可，省掉一个几 MB 的依赖。
 */
class ChapterPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ticker: Runnable? = null

    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 播放到结尾时回调，用来接下一章。 */
    var onCompleted: (() -> Unit)? = null

    fun play(url: String) {
        stop()
        error = null
        buffering = true
        val created = MediaPlayer()
        player = created
        try {
            created.setDataSource(url)
            created.setOnPreparedListener { prepared ->
                buffering = false
                durationMs = prepared.duration.toLong().coerceAtLeast(0L)
                prepared.start()
                playing = true
                startTicker()
            }
            created.setOnCompletionListener {
                playing = false
                stopTicker()
                onCompleted?.invoke()
            }
            created.setOnErrorListener { _, what, extra ->
                buffering = false
                playing = false
                stopTicker()
                error = "播放失败（$what/$extra）"
                true
            }
            created.prepareAsync()
        } catch (failure: Exception) {
            buffering = false
            error = failure.message ?: "无法播放该音频"
            stop()
        }
    }

    fun toggle() {
        val current = player ?: return
        if (current.isPlaying) {
            current.pause()
            playing = false
            stopTicker()
        } else {
            current.start()
            playing = true
            startTicker()
        }
    }

    fun seekTo(ms: Long) {
        val current = player ?: return
        current.seekTo(ms.toInt().coerceAtLeast(0))
        positionMs = ms
    }

    fun stop() {
        stopTicker()
        playing = false
        buffering = false
        positionMs = 0L
        durationMs = 0L
        player?.let { current ->
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        player = null
    }

    private fun startTicker() {
        stopTicker()
        val task = object : Runnable {
            override fun run() {
                positionMs = player?.currentPosition?.toLong() ?: 0L
                handler.postDelayed(this, 500)
            }
        }
        ticker = task
        handler.post(task)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }
}