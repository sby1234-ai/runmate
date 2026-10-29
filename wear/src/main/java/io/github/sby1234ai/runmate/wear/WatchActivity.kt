package io.github.sby1234ai.runmate.wear

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.util.Locale

/**
 * 워치 화면: 휴대폰 런메이트가 보내 주는 상태(시간·거리·걷기/뛰기)를 크게 보여 주고,
 * 「시작 / 일시정지·계속 / 종료(길게)」를 휴대폰으로 보냅니다. 위치 기록은 휴대폰이 합니다.
 *
 * 주고받는 메시지(휴대폰 WearSync와 같음)
 *  - 워치 → 휴대폰  /runmate/cmd   : start | pause | resume | stop | hello
 *  - 휴대폰 → 워치  /runmate/state : idle  또는  rec;paused(0/1);운동시간ms;거리m;상태글
 */
class WatchActivity : Activity(), MessageClient.OnMessageReceivedListener {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var time: TextView
    private lateinit var dist: TextView
    private lateinit var heart: TextView
    private lateinit var hint: TextView
    private lateinit var btnMain: TextView
    private lateinit var btnStop: TextView

    // 휴대폰에서 마지막으로 받은 상태
    private var recording = false
    private var paused = false
    private var activeMs = 0L
    private var receivedAt = 0L
    private var lastStateAt = 0L
    private var bpm = 0
    private var bpmSentAt = 0L
    private var measuring = false

    /** 심박 센서 값: 화면에 보이고, 기록 중이면 5초마다 휴대폰으로 */
    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
        override fun onDataReceived(data: DataPointContainer) {
            val v = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: return
            if (v <= 0) return
            main.post {
                bpm = v; heart.text = "♥ $v"
                val now = SystemClock.elapsedRealtime()
                if (recording && !paused && now - bpmSentAt >= 5000) { bpmSentAt = now; send("$v", "/runmate/hr") }
            }
        }
    }

    private fun hasSensor() = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

    private fun startHeart() {
        if (measuring || !hasSensor()) return
        try { HealthServices.getClient(this).measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback); measuring = true }
        catch (_: Exception) { heart.text = "" }
    }

    private fun stopHeart() {
        if (!measuring) return
        measuring = false
        try { HealthServices.getClient(this).measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startHeart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        // 둥근 화면도 글자가 잘리지 않게 가장자리를 넉넉히 비움
        val inset = (resources.displayMetrics.widthPixels * 0.12f).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(inset, inset / 2, inset, inset / 2)
            setBackgroundColor(0xFF0E141A.toInt())
        }
        fun label(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
        }
        status = label(13f, 0xB3FFFFFF.toInt(), true)
        time = label(34f, 0xFFFFFFFF.toInt(), true)
        dist = label(16f, 0xFFFFFFFF.toInt())
        heart = label(13f, 0xFFFF8A8A.toInt())
        hint = label(11f, 0x99FFFFFF.toInt())
        root.addView(status); root.addView(time); root.addView(dist); root.addView(heart)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun button(color: Int, textColor: Int) = TextView(this).apply {
            textSize = 13f; setTextColor(textColor); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(color) }
            isClickable = true; isFocusable = true
        }
        btnMain = button(0xFF1FB3A2.toInt(), 0xFF06221F.toInt())
        btnStop = button(0x40F2542D, 0xFFFFB3A1.toInt()).apply { text = "종료" }
        row.addView(btnMain, LinearLayout.LayoutParams(0, px(40f), 1f).apply { marginEnd = px(4f) })
        row.addView(btnStop, LinearLayout.LayoutParams(0, px(40f), 1f).apply { marginStart = px(4f) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })
        root.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4f) })
        setContentView(root)

        btnMain.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (!hasSensor()) requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1)
            send(when { !recording -> "start"; paused -> "resume"; else -> "pause" })
        }
        // 실수로 끝나지 않게 종료는 길게
        btnStop.setOnClickListener { hint.text = "종료는 길게 누르세요" }
        btnStop.setOnLongClickListener { it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); send("stop"); true }
        render()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        send("hello")      // 휴대폰에 지금 상태를 바로 보내 달라고 함
        main.post(tick)
        startHeart()
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        main.removeCallbacks(tick)
        stopHeart()
        super.onPause()
    }

    override fun onMessageReceived(e: MessageEvent) {
        if (e.path != "/runmate/state") return
        val s = String(e.data, Charsets.UTF_8)
        main.post {
            lastStateAt = SystemClock.elapsedRealtime()
            if (s == "idle") { recording = false; paused = false; activeMs = 0 }
            else {
                val f = s.split(";")
                recording = true
                paused = f.getOrNull(1) == "1"
                activeMs = f.getOrNull(2)?.toLongOrNull() ?: 0
                receivedAt = SystemClock.elapsedRealtime()
                dist.text = String.format(Locale.KOREA, "%.2f km", (f.getOrNull(3)?.toDoubleOrNull() ?: 0.0) / 1000.0)
                status.text = f.getOrNull(4) ?: ""
            }
            render()
        }
    }

    /** 시간은 받은 값에서 워치가 1초마다 이어 셈(메시지가 늦어도 부드럽게) */
    private val tick = object : Runnable {
        override fun run() {
            renderTime()
            // 휴대폰 소식이 6초 넘게 없으면 연결 안내
            val stale = SystemClock.elapsedRealtime() - lastStateAt > 6000
            if (stale && lastStateAt != 0L) hint.text = "휴대폰과 연결을 기다리는 중…"
            else if (lastStateAt == 0L) hint.text = "휴대폰 런메이트를 찾는 중…"
            main.postDelayed(this, 1000)
        }
    }

    private fun renderTime() {
        val ms = if (recording && !paused) activeMs + (SystemClock.elapsedRealtime() - receivedAt) else activeMs
        val sec = ms / 1000
        time.text = if (sec >= 3600) String.format(Locale.KOREA, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
        else String.format(Locale.KOREA, "%02d:%02d", sec / 60, sec % 60)
    }

    private fun render() {
        if (!recording) {
            status.text = "런메이트"; dist.text = "휴대폰과 함께 기록"
            heart.text = if (bpm > 0) "♥ $bpm" else if (hasSensor()) "" else "시작하면 심박 권한을 묻습니다"
            btnMain.text = "시작"; btnStop.visibility = View.GONE
            hint.text = "시작하면 휴대폰이 위치를 기록합니다"
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            btnMain.text = if (paused) "계속" else "일시정지"
            btnStop.visibility = View.VISIBLE
            hint.text = ""
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        renderTime()
    }

    private fun send(cmd: String, path: String = "/runmate/cmd") {
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) { if (path == "/runmate/hr") return@addOnSuccessListener; hint.text = "휴대폰이 연결되어 있지 않습니다"; return@addOnSuccessListener }
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, path, bytes) }
        }
    }
}
