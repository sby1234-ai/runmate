package io.github.sby1234ai.runmate

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.data.WeatherRepo
import io.github.sby1234ai.runmate.ui.MapPanel
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private var preview: Voice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val swMap = findViewById<MaterialSwitch>(R.id.swMap)
        val swVoice = findViewById<MaterialSwitch>(R.id.swVoice)
        val swPause = findViewById<MaterialSwitch>(R.id.swPause)
        swMap.isChecked = Prefs.map(this)
        swVoice.isChecked = Prefs.voice(this)
        swPause.isChecked = Prefs.autoPause(this)
        swMap.setOnCheckedChangeListener { _, v -> Prefs.setMap(this, v) }
        val swWeather = findViewById<MaterialSwitch>(R.id.swWeather)
        swWeather.isChecked = Prefs.weather(this)
        swWeather.setOnCheckedChangeListener { _, v -> Prefs.setWeather(this, v); if (!v) WeatherRepo.clear(this) }
        swVoice.setOnCheckedChangeListener { _, v -> Prefs.setVoice(this, v) }
        swPause.setOnCheckedChangeListener { _, v -> Prefs.setAutoPause(this, v) }
        if (RunRecorder.recording) {
            swVoice.isEnabled = false; swPause.isEnabled = false
            findViewById<TextView>(R.id.about).text = "기록 중에는 음성 안내와 자동 일시정지를 바꿀 수 없습니다. 다음 기록부터 적용됩니다.\n\n"
        }

        val swLock = findViewById<MaterialSwitch>(R.id.swLock)
        swLock.isChecked = Prefs.lockScreen(this)
        swLock.setOnCheckedChangeListener { _, v -> Prefs.setLockScreen(this, v) }
        // 안드로이드 14 이상에서 잠금 화면 표시(전체 화면 알림) 권한이 꺼져 있으면 켜는 버튼을 보여 줌
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            findViewById<MaterialButton>(R.id.btnFullScreen).apply {
                visibility = android.view.View.VISIBLE
                setOnClickListener {
                    try {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            android.net.Uri.parse("package:$packageName")))
                    } catch (e: Exception) { Toast.makeText(context, "설정 → 애플리케이션 → 런메이트에서 켜 주세요", Toast.LENGTH_LONG).show() }
                }
            }
        }
        val swUpdate = findViewById<MaterialSwitch>(R.id.swUpdate)
        swUpdate.isChecked = Updater.enabled(this)
        swUpdate.setOnCheckedChangeListener { _, v -> Updater.setEnabled(this, v) }
        findViewById<MaterialButton>(R.id.btnCheckUpdate).setOnClickListener { Updater.check(this, quiet = false) }

        findViewById<MaterialButton>(R.id.btnDevOptions).setOnClickListener {
            try { startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this, "개발자 옵션이 아직 켜져 있지 않습니다. 빌드 번호를 7번 눌러 켜 주세요", Toast.LENGTH_LONG).show() }
        }

        findViewById<MaterialButton>(R.id.btnTestVoice).setOnClickListener {
            val v = preview ?: Voice(this).also { preview = it }
            v.say("1킬로미터. 이번 1킬로미터 6분 12초. 운동 시간 6분 40초.")
        }
        findViewById<MaterialButton>(R.id.btnClearMap).setOnClickListener {
            MapPanel.clearCache(this)
            Toast.makeText(this, "받아 둔 지도 조각을 지웠습니다", Toast.LENGTH_SHORT).show()
            renderStorage()
        }
        renderStorage()
        val about = findViewById<TextView>(R.id.about)
        about.append("런메이트 ${BuildConfig.VERSION_NAME}\n지도 데이터 © OpenStreetMap 기여자 (ODbL). 지도 그림은 OpenStreetMap 재단 서버에서 받아 옵니다.\n날씨 정보: Open-Meteo.com (CC BY 4.0)\n소스: github.com/sby1234-ai/runmate")
    }

    private fun renderStorage() {
        fun size(f: File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { size(it) } ?: 0L) else f.length()
        val mapBytes = size(File(cacheDir, "osmdroid"))
        val runs = RunStore.ids(this).size
        findViewById<TextView>(R.id.storage).text = String.format(
            Locale.KOREA, "기록 %d개는 이 휴대폰의 앱 전용 공간에만 있습니다. 다시 볼 때 빠르게 뜨도록 받아 둔 지도: %.1f MB", runs, mapBytes / 1_048_576.0,
        )
    }

    override fun onDestroy() { preview?.shutdown(); super.onDestroy() }
}
