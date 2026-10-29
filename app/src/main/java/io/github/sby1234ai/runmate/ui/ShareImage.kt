package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.R
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.Stats

/**
 * 기록 한 개를 SNS에 올리기 좋은 세로 이미지(1080×1350)로 만듭니다.
 * 지도 그림 대신 경로 모양만 넣어, 집이나 회사 위치가 지도 위에 그대로 드러나지 않게 합니다.
 */
object ShareImage {
    fun render(ctx: Context, run: Run, s: Stats, dateText: String): Bitmap {
        val W = 1080; val H = 1350
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun col(id: Int) = ContextCompat.getColor(ctx, id)
        c.drawColor(col(R.color.bg))
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(size: Float, color: Int, b: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; textAlign = align
                if (b) typeface = bold
                fontFeatureSettings = "tnum"
            }
        val pad = 72f
        c.drawText("런메이트", pad, 120f, text(40f, col(R.color.run), true))
        c.drawText(dateText, pad, 176f, text(34f, col(R.color.sub)))

        val big = text(150f, col(R.color.ink), true)
        val km = Format.km(s.distance)
        c.drawText(km, pad, 340f, big)
        c.drawText("km", pad + big.measureText(km) + 16f, 340f, text(52f, col(R.color.sub)))

        // 경로 카드
        val card = RectF(pad, 390f, W - pad, 960f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(R.color.surface) }
        c.drawRoundRect(card, 44f, 44f, cardPaint)
        val inner = RectF(card.left + 56f, card.top + 56f, card.right - 56f, card.bottom - 56f)
        RouteDrawer(ctx, 12f, 15f).draw(c, inner, run.points)

        // 숫자 세 칸
        val labels = listOf("시간", "평균 페이스", "뛴 비율")
        val moving = (s.runDistance + s.walkDistance).coerceAtLeast(1.0)
        val values = listOf(
            Format.duration(s.moving),
            Format.pace(s.paceSecPerKm),
            "${(s.runDistance / moving * 100).toInt()}%",
        )
        val colW = (W - 2 * pad) / 3
        for (i in 0..2) {
            val x = pad + colW * i
            c.drawText(labels[i], x, 1040f, text(32f, col(R.color.sub)))
            c.drawText(values[i], x, 1112f, text(62f, if (i == 2) col(R.color.run) else col(R.color.ink), true))
        }

        // 뛰기·걷기 비율 막대
        val barTop = 1170f; val barH = 22f
        val runW = (W - 2 * pad) * (s.runDistance / moving).toFloat()
        c.drawRoundRect(RectF(pad, barTop, W - pad, barTop + barH), 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(R.color.walk) })
        if (runW > 0) c.drawRoundRect(RectF(pad, barTop, pad + runW, barTop + barH), 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(R.color.run) })
        c.drawText("뛰기 ${Format.km(s.runDistance)} km  ·  걷기 ${Format.km(s.walkDistance)} km", pad, 1250f, text(32f, col(R.color.sub)))
        return bmp
    }
}
