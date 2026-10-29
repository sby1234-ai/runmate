package io.github.sby1234ai.runmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.sby1234ai.runmate.logic.Motion

/**
 * 휴대폰 ↔ 워치 앱(웨어 OS) 연결.
 *  - 워치 → 휴대폰  /runmate/cmd   : start | pause | resume | stop | hello
 *  - 휴대폰 → 워치  /runmate/state : idle  또는  rec;paused(0/1);운동시간ms;거리m;상태글
 */
object WearSync {
    private var nodes: List<String> = emptyList()
    private var nodesAt = 0L
    private var lastPush = 0L

    /** 기록 서비스가 1초마다 부름: 연결된 워치에 지금 상태를 보냄(워치가 없으면 아무 일 없음) */
    fun push(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPush < 900) return
        lastPush = now
        val s = RunRecorder.snapshot()
        val msg = if (!s.recording) "idle" else {
            val state = when {
                s.manualPaused || s.autoPaused -> "일시정지"
                s.motion == Motion.RUN -> "뛰는 중"
                s.motion == Motion.WALK -> "걷는 중"
                else -> "기록 중"
            }
            "rec;${if (s.paused) 1 else 0};${s.activeNow()};${s.stats.distance.toLong()};$state"
        }
        val app = ctx.applicationContext
        val send = { ids: List<String> -> ids.forEach { Wearable.getMessageClient(app).sendMessage(it, "/runmate/state", msg.toByteArray(Charsets.UTF_8)) } }
        if (now - nodesAt < 30_000) send(nodes)
        else Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { list ->
            nodes = list.map { it.id }; nodesAt = System.currentTimeMillis(); send(nodes)
        }
    }

    /** 워치 「시작」: 앱 화면이 켜져 있으면 바로, 아니면 알림을 눌러 시작(안드로이드는 화면 밖에서 위치 기록을 바로 시작하지 못하게 막음) */
    fun startFromWatch(ctx: Context) {
        if (RunRecorder.recording) { push(ctx, true); return }
        if (MainActivity.visible) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).setAction(ACTION_WATCH_START).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watch_start", "워치에서 시작", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, 21, Intent(ctx, MainActivity::class.java).setAction(ACTION_WATCH_START)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(21, NotificationCompat.Builder(ctx, "watch_start")
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("워치에서 달리기 시작")
            .setContentText("눌러서 기록을 시작하세요")
            .setContentIntent(pi).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    const val ACTION_WATCH_START = "io.github.sby1234ai.runmate.WATCH_START"
}

/** 워치가 보낸 명령을 받는 곳(앱이 꺼져 있어도 시스템이 깨워 줌) */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(e: MessageEvent) {
        if (e.path != "/runmate/cmd") return
        when (String(e.data, Charsets.UTF_8)) {
            "start" -> WearSync.startFromWatch(this)
            "pause" -> if (RunRecorder.recording) RecordingService.pause(this, true)
            "resume" -> if (RunRecorder.recording) RecordingService.pause(this, false)
            "stop" -> if (RunRecorder.recording) RecordingService.stop(this)
            "hello" -> WearSync.push(this, true)
        }
        WearSync.push(this, true)
    }
}
