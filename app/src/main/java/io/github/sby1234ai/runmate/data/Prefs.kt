package io.github.sby1234ai.runmate.data

import android.content.Context

/** 설정값. 이 휴대폰 안에만 저장됩니다. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun map(ctx: Context) = p(ctx).getBoolean("map", true)
    fun setMap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("map", v).apply()

    fun voice(ctx: Context) = p(ctx).getBoolean("voice", true)
    fun setVoice(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("voice", v).apply()

    fun autoPause(ctx: Context) = p(ctx).getBoolean("autoPause", true)
    fun setAutoPause(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("autoPause", v).apply()

    /** 0이면 인터벌 없음. 나머지는 IntervalPlan.PRESETS의 id. */
    fun interval(ctx: Context) = p(ctx).getInt("interval", 0)
    fun setInterval(ctx: Context, id: Int) = p(ctx).edit().putInt("interval", id).apply()
}
