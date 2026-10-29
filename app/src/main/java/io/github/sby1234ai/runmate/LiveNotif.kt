package io.github.sby1234ai.runmate

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 안드로이드 16 「실시간 알림」(상태 표시줄 초록 칩·갤럭시 나우 바).
 *
 * - 실시간 알림 권한(POST_PROMOTED_NOTIFICATIONS)은 설치만으로 허용되고 기본값이 켜짐이라
 *   시스템이 「허용/거부」 팝업으로 묻지 않습니다. 사용자가 설정에서 끈 경우에만
 *   앱을 켤 때 대화상자로 알리고, 「켜기」를 누르면 그 스위치가 있는 설정 화면으로 바로 보냅니다.
 */
object LiveNotif {
    /** 안드로이드 16의 실시간 알림 설정 화면(앱별 스위치) */
    private const val ACTION_PROMOTION_SETTINGS = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
    private const val PREFS = "live_notif"
    private const val KEY_ASKED_DAY = "promote_asked_day"

    /**
     * 이 휴대폰이 다른 앱의 실시간 알림을 받아 주는지.
     * 안드로이드 16.0(SDK 36.0) 정식판은 실시간 알림이 꺼진 채 나왔고 권한 자체가 정의되어 있지 않아
     * canPostPromotedNotifications가 항상 false입니다(에뮬레이터 36.0으로 확인, 2026-10-29).
     * 다른 앱에 열린 건 SDK 36.1부터. Build.VERSION.SDK_INT_FULL(= 큰 번호 × 100000 + 작은 번호)은
     * 16.0에도 있어서(0.3.10에서 "값이 있으면 36.1"로 봤다가 갤럭시 16.0을 36.1로 잘못 판단) 숫자로 비교합니다.
     */
    fun supported(): Boolean = when {
        Build.VERSION.SDK_INT < 36 -> false
        Build.VERSION.SDK_INT > 36 -> true
        else -> try {
            val full = Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null)
            val major = full / 100_000; val minor = full % 100_000
            major > 36 || (major == 36 && minor >= 1)
        } catch (e: Exception) { false }
    }

    /** 실시간 알림이 켜져 있는지. 지원하지 않는 휴대폰이면 false */
    fun allowed(ctx: Context): Boolean {
        if (!supported()) return false
        return try {
            (NotificationManager::class.java.getMethod("canPostPromotedNotifications")
                .invoke(ctx.getSystemService(NotificationManager::class.java)) as Boolean)
                .also { if (BuildConfig.DEBUG) android.util.Log.d("RMLIVE", "canPostPromotedNotifications=$it") }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("RMLIVE", "canPostPromotedNotifications 실패: $e")
                // 이 휴대폰에 실제로 있는 실시간 알림 관련 함수 이름(점검용)
                val names = (NotificationManager::class.java.methods + android.app.Notification.Builder::class.java.methods)
                    .map { it.name }.filter { it.contains("romot", true) || it.contains("Critical", true) }.distinct()
                android.util.Log.d("RMLIVE", "methods=$names")
            }
            false
        }
    }

    /** 실시간 알림 스위치가 있는 설정 화면을 엶. 없으면 앱 알림 설정으로 */
    fun openSettings(ctx: Context) {
        val first = Intent(ACTION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        for (i in listOf(first, fallback)) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
        }
    }

    /**
     * 앱을 켤 때 부름. 필요한 안내가 있으면 대화상자를 하나만 띄웁니다.
     * 알림 권한이 아직 없으면(첫 실행 등) 묻지 않습니다 — 기록 시작 때 알림 권한부터 받기 때문.
     */
    fun checkOnStart(a: Activity) {
        if (a.isFinishing) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(a, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / 86_400_000L

        // 1) 실시간 알림이 꺼져 있으면 하루 한 번까지만 물어봄
        if (supported() && !allowed(a) && p.getLong(KEY_ASKED_DAY, -1) != today) {
            p.edit().putLong(KEY_ASKED_DAY, today).apply()
            MaterialAlertDialogBuilder(a)
                .setTitle("실시간 알림 켜기")
                .setMessage("달리는 중 거리·시간을 상태 표시줄 초록 칩에 보여 주려면 런메이트의 실시간 알림을 켜 주세요.\n\n「켜기」를 누르면 그 스위치가 있는 화면으로 이동합니다.")
                .setPositiveButton("켜기") { _, _ -> openSettings(a) }
                .setNegativeButton("다음에", null)
                .show()
        }
    }
}
