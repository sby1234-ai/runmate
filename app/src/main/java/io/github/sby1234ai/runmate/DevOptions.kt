package io.github.sby1234ai.runmate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * 설정 화면 「개발자 옵션 열기」 버튼.
 *
 * 다른 앱들처럼 설정 앱의 해당 항목으로 이동하면서 그 항목을 깜빡여 강조합니다.
 * 설정 앱은 `:settings:fragment_args_key`에 항목 키를 받으면 그 줄까지 스크롤해 강조합니다
 * (안드로이드 설정 검색 결과로 들어갈 때 쓰는 방식, 삼성 설정도 같은 키를 받음).
 *
 * - 개발자 옵션이 꺼져 있으면: 휴대전화 정보(삼성은 소프트웨어 정보) → 「빌드 번호」 강조
 * - 켜져 있으면: 개발자 옵션 → 「모든 앱의 라이브 알림」(삼성 One UI 8 이상) 강조
 *   삼성 항목 키는 공개되어 있지 않아 후보 키를 넣었고, 맞지 않으면 개발자 옵션 첫 화면만 열림.
 *   그래서 이동한 뒤 무엇을 켜야 하는지 안내 글을 함께 띄움.
 */
object DevOptions {
    private const val ARGS_KEY = ":settings:fragment_args_key"
    private const val ARGS_BUNDLE = ":settings:show_fragment_args"

    /** 빌드 번호 항목 키 (AOSP·삼성 공통) */
    private const val KEY_BUILD_NUMBER = "build_number"

    /** 삼성 「모든 앱의 라이브 알림」 항목 키 후보 (사용자 휴대폰에서 강조되는지 확인 필요) */
    private const val KEY_LIVE_ALL_APPS = "live_notifications_for_all_apps"

    fun enabled(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    fun open(ctx: Context) {
        if (enabled(ctx)) {
            val ok = launch(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), KEY_LIVE_ALL_APPS)
            if (ok) toast(ctx, "「모든 앱의 라이브 알림」을 찾아 켜 주세요. 아래쪽 「알림」 근처에 있습니다")
            else toast(ctx, "개발자 옵션을 열 수 없습니다. 설정 → 개발자 옵션에서 「모든 앱의 라이브 알림」을 켜 주세요")
            return
        }
        // 삼성: 빌드 번호는 휴대전화 정보 안의 「소프트웨어 정보」에 있음 → 그 화면을 먼저 시도
        val samsungSoftwareInfo = Intent().setComponent(
            ComponentName("com.android.settings", "com.android.settings.Settings\$SoftwareInfoSettingsActivity"),
        )
        val ok = launch(ctx, samsungSoftwareInfo, KEY_BUILD_NUMBER) ||
            launch(ctx, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), KEY_BUILD_NUMBER)
        if (ok) toast(ctx, "깜빡이는 「빌드 번호」를 7번 눌러 개발자 옵션을 켠 뒤 런메이트로 돌아와 버튼을 다시 눌러 주세요")
        else toast(ctx, "설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드 번호를 7번 눌러 주세요")
    }

    /** 항목 강조 키를 붙여 설정 화면을 엶. 열 수 있는 화면이 없거나 막히면 false */
    private fun launch(ctx: Context, intent: Intent, key: String): Boolean {
        intent.putExtra(ARGS_KEY, key)
        intent.putExtra(ARGS_BUNDLE, Bundle().apply { putString(ARGS_KEY, key) })
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 안드로이드 11 이상은 패키지 가시성 때문에 resolveActivity가 null일 수 있어, 바로 열어 보고 실패하면 다음 후보로
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}
