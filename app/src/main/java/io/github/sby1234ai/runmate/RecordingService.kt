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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import io.github.sby1234ai.runmate.data.CheckpointStore
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
        private const val ACTION_RESTORE = "restore"
        private const val ACTION_REPOST = "repost"
        const val ACTION_STOP_FROM_NOTIF = "io.github.sby1234ai.runmate.STOP"
        private const val RESTORE_NOTIF_ID = 2
        const val LOCK_NOTIF_ID = 3
        private const val LOCK_CHANNEL = "lockscreen"
        // 잠금 화면에서도 보이도록 '조용한 알림'이 아닌 기본 등급(소리·진동 없음)으로 새 채널을 씁니다
        const val CHANNEL_ID = "recording_v2"
        private const val CHANNEL = CHANNEL_ID
        private const val OLD_CHANNEL = "recording"
        private const val NOTIF_ID = 1
        private const val AR_REQUEST = 7

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java).setAction(ACTION_START))
        }

        /** 강제로 꺼졌던 기록을 중간 저장본에서 이어서 기록 */
        fun restore(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java).setAction(ACTION_RESTORE))
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
    private var lastPaused = false
    private var lastSavedAt = 0L
    private var voice: Voice? = null
    private var coach: Coach? = null
    private val ticker = Handler(Looper.getMainLooper())

    // 1초마다 음성 안내할 것이 있는지 봅니다(인터벌은 GPS가 잠시 끊겨도 시간대로 진행)
    private val tick = object : Runnable {
        override fun run() {
            val s = RunRecorder.snapshot()
            if (!s.recording) return
            val now = System.currentTimeMillis()
            val cues = coach?.update(now, s.startedAt, s.activeNow(), s.stats, s.motion, s.paused) ?: emptyList()
            cues.forEach { voice?.say(it) }
            // 5초마다, 그리고 멈춤↔다시 시작이 바뀌는 순간 바로 알림(잠금 화면 카드)을 고칩니다
            if (now - lastSavedAt >= 10_000) { lastSavedAt = now; saveCheckpoint() }
            if (now - lastNotifAt > 5000 || s.paused != lastPaused) { lastNotifAt = now; lastPaused = s.paused; updateNotification() }
            WearSync.push(this@RecordingService)   // 워치 앱에 시간·거리
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
            ACTION_PAUSE -> { RunRecorder.setManualPause(true); updateNotification() }
            ACTION_RESUME -> { RunRecorder.setManualPause(false); updateNotification() }
            ACTION_RESTORE -> begin(restore = true)
            ACTION_REPOST -> if (RunRecorder.recording) updateNotification()
            // intent가 없으면 시스템이 강제로 껐던 서비스를 다시 살린 것: 중간 저장본에서 이어서 기록
            null -> if (intent == null) begin(restore = true) else begin()
            else -> begin()
        }
        // 기록 중에 휴대폰이 앱을 강제로 끄면 시스템이 이 서비스를 다시 살리도록(START_STICKY)
        return if (RunRecorder.recording) START_STICKY else START_NOT_STICKY
    }

    private fun begin(restore: Boolean = false) {
        createChannel()
        val cp = if (restore && !RunRecorder.recording) CheckpointStore.load(this) else null
        if (restore && !RunRecorder.recording && cp == null) { stopSelf(); return }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(if (cp != null) "기록을 이어서 합니다" else "기록을 시작했습니다"), type)
        } catch (e: Exception) {
            // 앱이 화면에 없을 때는 안드로이드가 위치 기록 서비스를 다시 켜지 못하게 막을 수 있음 → 눌러서 이어 가도록 안내
            if (cp != null) askToResume()
            stopSelf(); return
        }
        if (RunRecorder.recording) return
        val plan: IntervalPlan?
        val autoPause: Boolean
        if (cp != null) {
            RunRecorder.restore(cp)
            plan = IntervalPlan.byId(cp.planId); autoPause = cp.autoPause
            getSystemService(NotificationManager::class.java).cancel(RESTORE_NOTIF_ID)
        } else {
            plan = IntervalPlan.byId(Prefs.interval(this))
            autoPause = Prefs.autoPause(this)
            RunRecorder.start(System.currentTimeMillis(), plan, autoPause)
        }
        acquireWakeLock()
        startSteps()
        watchScreen()
        if (Prefs.voice(this)) {
            voice = Voice(this)
            coach = Coach(plan, autoPause).also { c ->
                if (cp != null) RunRecorder.snapshot().let { c.prime(it.stats, it.paused) }
            }
            if (cp != null) voice?.say("기록을 이어서 합니다.")
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

    // 걸음 센서: 하드웨어 걸음 계수기(TYPE_STEP_COUNTER). 권한(신체 활동)이 없거나 센서가 없으면 걸음 수 없이 기록합니다
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { RunRecorder.onSteps(e.values[0]) }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun startSteps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        val sm = getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI, 0)   // 모아 두지 말고 바로 알려 달라(0)
    }

    private fun stopSteps() { getSystemService(SensorManager::class.java).unregisterListener(stepListener) }

    // 10초마다 진행 중인 기록을 저장(앱이 강제로 꺼져도 이어서 기록하기 위해). 파일 쓰기는 뒤에서
    private fun saveCheckpoint() {
        val cp = RunRecorder.checkpoint() ?: return
        val app = applicationContext
        Thread { try { CheckpointStore.saveIfRecording(app, cp) { RunRecorder.recording } } catch (_: Exception) {} }.start()
    }

    private fun askToResume() {
        val open = PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("런메이트 기록이 잠시 멈췄어요")
            .setContentText("눌러서 앱을 열면 멈춘 곳부터 이어서 기록합니다")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESTORE_NOTIF_ID, n)
    }

    private fun finish() {
        unwatchScreen()
        releaseWakeLock()
        stopSteps()
        fused.removeLocationUpdates(callback)
        ticker.removeCallbacks(tick)
        voice?.shutdown(); voice = null; coach = null
        if (arPermissionGranted()) {
            try { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(arPendingIntent()) } catch (_: SecurityException) {}
        }
        val run = RunRecorder.stop()
        CheckpointStore.clear(this)
        if (run != null) {
            RunStore.save(this, run)
            RunRecorder.markSaved(run.id)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        WearSync.push(this, true)   // 워치에 「끝남」
        unwatchScreen()
        releaseWakeLock()
        stopSteps()
        fused.removeLocationUpdates(callback)
        ticker.removeCallbacks(tick)
        voice?.shutdown(); voice = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            try { nm.deleteNotificationChannel(OLD_CHANNEL) } catch (_: Exception) {}
            val ch = NotificationChannel(CHANNEL, "러닝 기록 중", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "기록하는 동안 거리와 시간을 보여 줍니다(잠금 화면 포함)"
            ch.setSound(null, null); ch.enableVibration(false); ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
            // 화면이 꺼질 때 잠금 화면 위에 달리기 화면을 띄우는 용도(소리·진동 없음)
            val lock = NotificationChannel(LOCK_CHANNEL, "잠금 화면 달리기 화면", NotificationManager.IMPORTANCE_HIGH)
            lock.description = "달리는 중 화면이 꺼지면 잠금 화면에 달리기 화면을 보여 줍니다"
            lock.setSound(null, null); lock.enableVibration(false)
            lock.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(lock)
        }
    }

    // ───── 잠금 화면 위 달리기 화면(삼성 헬스처럼) ─────
    // 화면이 꺼지는 순간 '전체 화면 알림'으로 달리기 화면을 잠금 위에 올려 둡니다(알람 앱과 같은 방식).
    // 그래서 다른 앱을 보다가 꺼져도, 다시 켜면 잠금을 풀지 않고 시간·거리·일시정지를 볼 수 있습니다.
    private var screenReceiverOn = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> if (RunRecorder.recording) showOverLockScreen()
                Intent.ACTION_USER_PRESENT -> getSystemService(NotificationManager::class.java).cancel(LOCK_NOTIF_ID)
            }
        }
    }

    private fun watchScreen() {
        if (screenReceiverOn) return
        val f = android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverOn = true
    }

    private fun unwatchScreen() {
        if (!screenReceiverOn) return
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        screenReceiverOn = false
        getSystemService(NotificationManager::class.java).cancel(LOCK_NOTIF_ID)
    }

    private fun showOverLockScreen() {
        if (!Prefs.lockScreen(this)) return
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) return
        val pi = PendingIntent.getActivity(this, 9,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, LOCK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("런메이트 달리는 중")
            .setContentText("잠금 화면에서 기록을 보여 줍니다")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setFullScreenIntent(pi, true)        // 무음(setSilent)으로 하면 잠금 화면에 띄우지 않으므로 채널에서 소리만 끔
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        nm.notify(LOCK_NOTIF_ID, n)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snap = RunRecorder.snapshot()
        val paused = snap.paused   // 자동으로 멈춘 경우도 「다시 시작」을 보여 줌
        val toggle = PendingIntent.getService(this, 3,
            Intent(this, RecordingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canPromote()) return liveUpdate(text, snap, open, toggle, paused)
        return NotificationCompat.Builder(this, CHANNEL)
            .addExtras(samsungExtras(snap))
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(if (paused) "런메이트 일시정지" else "런메이트 기록 중")
            .setContentText(text)
            // 삼성 헬스처럼 잠금 화면에서도 앱 화면 같은 카드: 어두운 바탕, 초 단위로 흐르는 큰 시간, 거리·페이스
            .setCustomContentView(runViews(R.layout.notif_run_small, snap))
            .setCustomBigContentView(runViews(R.layout.notif_run_big, snap))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setColorized(true)
            .setColor(0xFF141B24.toInt())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)       // 잠금 화면에서도 내용이 보이게
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // 삼성 헬스처럼 버튼 두 개: 「일시정지/계속」 | 「종료」
            .addAction(0, if (paused) "계속" else "일시정지", toggle)
            .addAction(0, "종료", stopIntent())
            // 종료하기 전에는 옆으로 밀어서 지워도 바로 다시 나타나게
            .setDeleteIntent(repostIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ───── 안드로이드 16 이상: 「실시간 알림」(상태 표시줄 초록 칩·갤럭시 나우 바에 거리·시간 표시) ─────
    // 실시간 알림은 직접 꾸민 카드·색 채운 알림을 허용하지 않으므로, 이 경우엔 표준 알림으로 같은 정보를 보여 줍니다.
    // 0.3.9부터 안드로이드 16(targetSdk 36) 기준으로 빌드해야 시스템이 실시간 알림 대상으로 봅니다.
    // 16의 새 함수는 안드로이드 16 미만 휴대폰에서도 안전하도록 이름으로 찾아 부릅니다(없으면 건너뜀).

    /**
     * 삼성 라이브 알림·나우 바용 추가 정보(삼성 확장 키). 삼성이 받아 주는 경우에만 쓰이고, 아니면 무시됩니다.
     */
    private fun samsungExtras(s: RunRecorder.Snapshot): android.os.Bundle {
        val nowE = android.os.SystemClock.elapsedRealtime()
        val active = if (s.recording) s.activeMs(nowE) else 0L
        val lap = io.github.sby1234ai.runmate.logic.Lap.of(s.stats, active)
        val km = "${Format.km(s.stats.distance)} km"
        val time = android.text.format.DateUtils.formatElapsedTime(active / 1000)
        val state = when {
            s.manualPaused || s.autoPaused -> "일시정지"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.RUN -> "뛰는 중"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.WALK -> "걷는 중"
            else -> "기록 중"
        }
        return android.os.Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", "$km · $time")
            putString("android.ongoingActivityNoti.secondaryInfo", "$state · ${lap.number}km째 ${Format.pace(lap.paceSecPerKm)}")
            putString("android.ongoingActivityNoti.chipExpandedText", km)
            putInt("android.ongoingActivityNoti.chipBgColor", 0xFF1FB3A2.toInt())
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", km)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", "$time · $state")
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
        }
    }

    private fun canPromote(): Boolean = LiveNotif.allowed(this)

    private fun liveUpdate(text: String, s: RunRecorder.Snapshot, open: PendingIntent, toggle: PendingIntent, paused: Boolean): Notification {
        val nowE = android.os.SystemClock.elapsedRealtime()
        val active = if (s.recording) s.activeMs(nowE) else 0L
        val lap = io.github.sby1234ai.runmate.logic.Lap.of(s.stats, active)
        val state = when {
            !s.recording -> "준비 중"
            s.manualPaused -> "일시정지"
            s.autoPaused -> "자동 일시정지"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.RUN -> "뛰는 중"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.WALK -> "걷는 중"
            else -> "멈춤"
        }
        val detail = buildList {
            add("${Format.km(s.stats.distance)} km · 평균 ${Format.pace(s.stats.paceSecPerKm)}")
            add("${lap.number}km째 ${Format.pace(lap.paceSecPerKm)}")
            if (s.steps > 0) add(String.format(java.util.Locale.KOREA, "%,d걸음", s.steps))
            s.cadence?.takeIf { it > 0 }?.let { add("분당 ${it}걸음") }
        }.joinToString(" · ")
        val n = NotificationCompat.Builder(this, CHANNEL)
            .addExtras(samsungExtras(s))
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("런메이트 · $state")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setColor(0xFF1FB3A2.toInt())                 // 칩·아이콘 강조색(초록 계열)
            .setUsesChronometer(s.recording && !paused)     // 칩과 알림에 흐르는 운동 시간
            .setWhen(System.currentTimeMillis() - active)
            .setShowWhen(s.recording && !paused)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // 삼성 헬스처럼 버튼 두 개: 「일시정지/계속」 | 「종료」
            .addAction(0, if (paused) "계속" else "일시정지", toggle)
            .addAction(0, "종료", stopIntent())
            // 종료하기 전에는 옆으로 밀어서 지워도 바로 다시 나타나게
            .setDeleteIntent(repostIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val b = try { Notification.Builder.recoverBuilder(this, n) } catch (e: Exception) { return n }
        // 실시간 알림 요청(이게 있어야 칩·나우 바에 올라감). 짧은 글이 실패해도 요청은 살아 있게 따로 부름
        try {
            Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true)
        } catch (e: Exception) { return n }
        // 칩에 보일 짧은 글(7자 미만이면 다 보임): 멈추면 "일시정지", 달리면 거리
        try {
            Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
                .invoke(b, if (paused) "일시정지" else "${Format.km(s.stats.distance)}km")
        } catch (e: Exception) { }
        return b.build()
    }

    /** 알림의 「종료」: 앱을 열어 기록을 끝내고 결과 화면을 보여 줍니다(저장도 앱에서). */
    private fun stopIntent(): PendingIntent = PendingIntent.getActivity(this, 11,
        Intent(this, MainActivity::class.java).setAction(ACTION_STOP_FROM_NOTIF).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** 알림을 밀어서 지우면 이 서비스로 신호가 와서 같은 알림을 다시 올립니다. */
    private fun repostIntent(): PendingIntent = PendingIntent.getService(this, 12,
        Intent(this, RecordingService::class.java).setAction(ACTION_REPOST),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** 알림 카드 내용. 시간은 Chronometer라 앱이 고치지 않아도 잠금 화면에서 1초마다 흐릅니다(멈춤이면 멈춤). */
    private fun runViews(layout: Int, s: RunRecorder.Snapshot): android.widget.RemoteViews {
        val v = android.widget.RemoteViews(packageName, layout)
        // 앱 화면과 똑같은 기준점: 달리는 중이면 base = 시작 시각 + 멈춘 시간 합계(값이 변하지 않음)
        val nowE = android.os.SystemClock.elapsedRealtime()
        val active = if (s.recording) s.activeMs(nowE) else 0L
        val base = nowE - active
        if (BuildConfig.DEBUG) android.util.Log.d("RMTIME", "notif $nowE $base ${s.recording && !s.paused}")
        v.setChronometer(R.id.nTime, base, null, s.recording && !s.paused)
        v.setTextViewText(R.id.nDist, if (layout == R.layout.notif_run_small) "${Format.km(s.stats.distance)} km" else Format.km(s.stats.distance))
        val state = when {
            !s.recording -> "준비 중"
            s.manualPaused -> "일시정지"
            s.autoPaused -> "자동 일시정지"
            s.lastAccuracy == null -> "GPS 찾는 중"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.RUN -> "뛰는 중"
            s.motion == io.github.sby1234ai.runmate.logic.Motion.WALK -> "걷는 중"
            else -> "멈춤"
        }
        val lap = io.github.sby1234ai.runmate.logic.Lap.of(s.stats, active)
        v.setTextViewText(R.id.nState, if (layout == R.layout.notif_run_small && s.recording) "${lap.number}km째 · $state" else state)
        if (layout == R.layout.notif_run_big) {
            v.setTextViewText(R.id.nPace, Format.pace(s.stats.paceSecPerKm))
            v.setTextViewText(R.id.nRun, Format.km(s.stats.runDistance))
            val extra = buildList {
                add("${lap.number}km째 ${Format.pace(lap.paceSecPerKm)}")
                if (s.steps > 0) add(String.format(java.util.Locale.KOREA, "%,d걸음", s.steps))
                s.cadence?.takeIf { it > 0 }?.let { add("분당 ${it}걸음") }
                s.plan?.let { p ->
                    val ph = p.phaseAt(active / 1000)
                    add(when (ph.kind) {
                        io.github.sby1234ai.runmate.logic.IntervalPlan.Kind.WARMUP -> "준비 걷기"
                        io.github.sby1234ai.runmate.logic.IntervalPlan.Kind.RUN -> "인터벌 ${ph.rep}/${p.reps} 뛰기"
                        io.github.sby1234ai.runmate.logic.IntervalPlan.Kind.WALK -> "인터벌 ${ph.rep}/${p.reps} 걷기"
                        io.github.sby1234ai.runmate.logic.IntervalPlan.Kind.DONE -> "인터벌 완료"
                    })
                }
            }
            v.setTextViewText(R.id.nExtra, extra.joinToString("  ·  "))
            v.setInt(R.id.nState, "setBackgroundResource", when (state) {
                "뛰는 중" -> R.drawable.bg_notif_chip
                "걷는 중" -> R.drawable.bg_notif_chip_walk
                else -> R.drawable.bg_notif_chip_pause
            })
        }
        return v
    }

    private fun updateNotification() {
        val s = RunRecorder.snapshot()
        val now = System.currentTimeMillis()
        val time = s.activeNow()
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
