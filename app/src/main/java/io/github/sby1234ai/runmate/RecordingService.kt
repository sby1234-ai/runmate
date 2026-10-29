package io.github.sby1234ai.runmate

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.logic.Coach
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.IntervalPlan

/**
 * 기록 중에는 알림을 띄운 채로 도는 서비스. 화면을 끄거나 다른 앱으로 가도 위치를 계속 받습니다.
 * 기록을 시작하는 순간 앱이 화면에 떠 있으므로 '앱 사용 중에만 허용' 위치 권한으로 충분합니다.
 */
class RecordingService : Service() {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 1
        private const val AR_REQUEST = 7

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java).setAction(ACTION_START))
        }

        fun pause(ctx: Context, on: Boolean) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(if (on) ACTION_PAUSE else ACTION_RESUME))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastNotifAt = 0L
    private var voice: Voice? = null
    private var coach: Coach? = null
    private val ticker = Handler(Looper.getMainLooper())

    // 1초마다 음성 안내할 것이 있는지 봅니다(인터벌은 GPS가 잠시 끊겨도 시간대로 진행)
    private val tick = object : Runnable {
        override fun run() {
            val s = RunRecorder.snapshot()
            if (!s.recording) return
            val now = System.currentTimeMillis()
            val cues = coach?.update(now, s.startedAt, s.activeMs(now), s.stats, s.motion, s.paused) ?: emptyList()
            cues.forEach { voice?.say(it) }
            if (now - lastNotifAt > 5000) { lastNotifAt = now; updateNotification() }
            ticker.postDelayed(this, 1000)
        }
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) RunRecorder.onLocation(loc)
            val now = System.currentTimeMillis()
            if (now - lastNotifAt > 5000) { lastNotifAt = now; updateNotification() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish()
            // 음성 안내("일시정지"/"다시 시작")는 Coach가 상태 변화를 보고 한 번만 말합니다
            ACTION_PAUSE -> { RunRecorder.setManualPause(true, System.currentTimeMillis()); updateNotification() }
            ACTION_RESUME -> { RunRecorder.setManualPause(false, System.currentTimeMillis()); updateNotification() }
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("기록을 시작했습니다"), type)
        if (RunRecorder.recording) return
        val plan = IntervalPlan.byId(Prefs.interval(this))
        val autoPause = Prefs.autoPause(this)
        RunRecorder.start(System.currentTimeMillis(), plan, autoPause)
        acquireWakeLock()
        if (Prefs.voice(this)) {
            voice = Voice(this)
            coach = Coach(plan, autoPause)
        }
        ticker.post(tick)
        try {
            // 화면이 꺼져도 위치를 모아 두었다 한꺼번에 주지 말고(배치 없음) 2초마다 바로 받게 합니다
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdateDelayMillis(2000L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            RunRecorder.stop(); stopSelf(); return
        }
        RunRecorder.usingActivityRecognition = startActivityRecognition()
    }

    private fun arPermissionGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION
        else "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun arPendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(this, AR_REQUEST, Intent(this, ActivityTransitionReceiver::class.java), flags)
    }

    private fun startActivityRecognition(): Boolean {
        if (!arPermissionGranted()) return false
        val transitions = listOf(DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.STILL).map {
            ActivityTransition.Builder()
                .setActivityType(it)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        return try {
            ActivityRecognition.getClient(this).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), arPendingIntent())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    // 화면이 꺼지거나 잠금 상태여도 기록이 계속되도록, 기록하는 동안만 CPU가 잠들지 않게 붙잡아 둡니다(화면은 꺼져도 됨)
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runmate:recording")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }   // 최대 6시간
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun finish() {
        releaseWakeLock()
        fused.removeLocationUpdates(callback)
        ticker.removeCallbacks(tick)
        voice?.shutdown(); voice = null; coach = null
        if (arPermissionGranted()) {
            try { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(arPendingIntent()) } catch (_: SecurityException) {}
        }
        val run = RunRecorder.stop()
        if (run != null) {
            RunStore.save(this, run)
            RunRecorder.markSaved(run.id)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        fused.removeLocationUpdates(callback)
        ticker.removeCallbacks(tick)
        voice?.shutdown(); voice = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "러닝 기록 중", NotificationManager.IMPORTANCE_LOW)
            ch.description = "기록하는 동안 거리와 시간을 보여 줍니다"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manual = RunRecorder.snapshot().paused   // 자동으로 멈춘 경우도 「다시 시작」을 보여 줌
        val toggle = PendingIntent.getService(this, 3,
            Intent(this, RecordingService::class.java).setAction(if (manual) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(if (manual) "런메이트 일시정지" else "런메이트 기록 중")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)       // 잠금 화면에서도 거리·시간이 보이게
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (manual) "다시 시작" else "일시정지", toggle)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val s = RunRecorder.snapshot()
        val now = System.currentTimeMillis()
        val time = s.activeMs(now)
        val state = when {
            s.manualPaused -> "일시정지"
            s.autoPaused -> "멈춰 있어 자동 일시정지"
            s.lastAccuracy == null -> "GPS 찾는 중"
            else -> "지금 ${s.motion.label}"
        }
        val text = "${Format.km(s.stats.distance)} km · ${Format.duration(time)} · $state"
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }
}
