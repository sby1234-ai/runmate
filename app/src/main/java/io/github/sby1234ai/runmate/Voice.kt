package io.github.sby1234ai.runmate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 휴대폰에 들어 있는 음성 합성(TTS)으로 안내를 읽습니다. 음악을 듣고 있으면 잠깐 소리를 줄였다가 되돌립니다.
 * 준비되기 전에 들어온 말은 모아 두었다가 준비되면 읽습니다.
 */
class Voice(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    private val pending = ArrayList<String>()
    private var ready = false
    private var seq = 0
    @Volatile private var speaking = 0

    private lateinit var tts: TextToSpeech

    init { tts = TextToSpeech(ctx.applicationContext) { status -> onInit(status) } }

    private fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.KOREAN)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setAudioAttributes(attrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = finished()
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = finished()
            })
            if (ready) { pending.forEach { say(it) }; pending.clear() }
        }
    }

    private fun finished() {
        speaking = (speaking - 1).coerceAtLeast(0)
        if (speaking == 0) audio.abandonAudioFocusRequest(focus)
    }

    fun say(text: String) {
        if (!ready) { if (pending.size < 5) pending.add(text); return }
        audio.requestAudioFocus(focus)
        speaking++
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "rm${seq++}")
    }

    fun shutdown() {
        tts.stop(); tts.shutdown()
        audio.abandonAudioFocusRequest(focus)
    }
}
