package io.github.sby1234ai.runmate.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 안드로이드 15 이상 기준(targetSdk 35+)으로 빌드하면 모든 화면이 상태 표시줄·내비게이션 바 뒤까지 그려집니다.
 * 글자·버튼이 가려지지 않게, 화면 내용 전체를 그 높이만큼 안쪽으로 밀어 줍니다.
 * 안드로이드 14 이하에서도 똑같이 보이도록 같은 방식(뒤까지 그리고 안쪽으로 밀기)으로 맞춥니다.
 * 첫 화면(MainActivity)은 배경 사진을 뒤까지 깔려고 따로 처리하므로 여기 쓰지 않습니다.
 */
object EdgeInsets {
    fun apply(a: Activity) {
        WindowCompat.setDecorFitsSystemWindows(a.window, false)
        @Suppress("DEPRECATION")
        run {
            a.window.statusBarColor = 0x00000000
            a.window.navigationBarColor = 0x00000000
        }
        val content = a.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
    }
}
