package io.github.sby1234ai.runmate

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.sby1234ai.runmate.logic.Update
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 새 버전 확인 → 받기 → 설치 화면 열기.
 * - 하루 한 번(또는 설정의 「지금 확인」) GitHub 최신 릴리스를 봅니다.
 * - 받은 파일의 SHA-256이 릴리스에 함께 올린 값과 다르면 설치하지 않습니다.
 * - 안드로이드도 지금 앱과 같은 서명 키로 만든 파일만 업데이트로 받아 줍니다.
 * - 플레이 스토어 밖 앱은 사용자가 「설치」를 한 번 눌러야 합니다(완전 자동 설치는 안드로이드가 막음).
 */
object Updater {
    private const val API = "https://api.github.com/repos/sby1234-ai/runmate/releases/latest"
    private const val DAY = 24 * 60 * 60 * 1000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("autoUpdate", true)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autoUpdate", v).apply()

    /** 앱을 열 때: 켜져 있고 하루가 지났으면 조용히 확인하고, 새 버전이 있을 때만 묻습니다. */
    fun checkDaily(a: Activity, currentOverride: String? = null) {
        if (!enabled(a) || RunRecorder.recording) return
        val last = prefs(a).getLong("updateCheckedAt", 0L)
        if (currentOverride == null && System.currentTimeMillis() - last < DAY) return
        check(a, quiet = true, currentOverride = currentOverride)
    }

    fun check(a: Activity, quiet: Boolean, currentOverride: String? = null, autoConfirm: Boolean = false) {
        if (busy) return
        busy = true
        Thread {
            val rel = try { Update.parse(get(API)) } catch (e: Exception) { null }
            busy = false
            main.post {
                if (a.isFinishing) return@post
                prefs(a).edit().putLong("updateCheckedAt", System.currentTimeMillis()).apply()
                val current = currentOverride ?: BuildConfig.VERSION_NAME
                when {
                    rel == null -> if (!quiet) Toast.makeText(a, "새 버전을 확인하지 못했습니다. 인터넷 연결을 확인해 주세요", Toast.LENGTH_LONG).show()
                    !Update.isNewer(rel.version, current) -> if (!quiet) Toast.makeText(a, "지금이 최신 버전입니다 ($current)", Toast.LENGTH_SHORT).show()
                    autoConfirm -> download(a, rel)
                    else -> ask(a, rel, current)
                }
            }
        }.start()
    }

    private fun ask(a: Activity, rel: Update.Release, current: String) {
        val notes = rel.notes.lineSequence().filter { it.isNotBlank() }.take(8).joinToString("\n")
        MaterialAlertDialogBuilder(a)
            .setTitle("새 버전 ${rel.version}이 있어요")
            .setMessage("지금 버전: $current\n\n$notes\n\n받은 뒤 「설치」만 누르면 됩니다. 기록은 그대로 남습니다.")
            .setPositiveButton("업데이트") { _, _ -> download(a, rel) }
            .setNegativeButton("나중에", null)
            .show()
    }

    private fun download(a: Activity, rel: Update.Release) {
        val dlg = MaterialAlertDialogBuilder(a).setTitle("새 버전 받는 중…").setMessage("잠시만 기다려 주세요").setCancelable(false).show()
        Thread {
            val result: Result<File> = runCatching {
                val dir = File(a.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val apk = File(dir, "runmate-${rel.version}.apk")
                download(rel.apkUrl, apk)
                val expected = rel.shaUrl?.let { Update.shaFrom(get(it)) }
                    ?: error("확인용 SHA-256 값이 릴리스에 없습니다")
                val actual = sha256(apk)
                if (actual != expected) { apk.delete(); error("받은 파일이 릴리스와 다릅니다(SHA-256 불일치)") }
                apk
            }
            main.post {
                dlg.dismiss()
                if (a.isFinishing) return@post
                result.onSuccess { install(a, it) }
                    .onFailure { Toast.makeText(a, "업데이트하지 못했습니다: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    /** 처음 한 번은 「출처를 알 수 없는 앱 설치」 허용이 필요합니다. 허용 화면에서 돌아오면 다시 확인합니다. */
    fun install(a: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "런메이트에 「이 출처 허용」을 켠 뒤 뒤로 오면 설치가 이어집니다", Toast.LENGTH_LONG).show()
            prefs(a).edit().putString("pendingApk", apk.absolutePath).apply()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        prefs(a).edit().remove("pendingApk").apply()
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", apk)
        a.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 허용 화면에서 돌아왔을 때 받아 둔 파일이 있으면 설치를 이어 갑니다. */
    fun resumePending(a: Activity) {
        val path = prefs(a).getString("pendingApk", null) ?: return
        val f = File(path)
        if (!f.exists()) { prefs(a).edit().remove("pendingApk").apply(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && a.packageManager.canRequestPackageInstalls()) install(a, f)
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "RunMate/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    private fun get(url: String): String = open(url).let { c -> c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() } }

    private fun download(url: String, to: File) {
        val c = open(url)
        c.inputStream.use { inp -> to.outputStream().use { inp.copyTo(it) } }
        c.disconnect()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
