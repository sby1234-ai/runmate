package io.github.sby1234ai.runmate.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.BuildConfig
import io.github.sby1234ai.runmate.R
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * 경로를 보여 주는 칸. 설정에서 지도를 켜 두면 OpenStreetMap 지도 위에, 끄면 지도 없이 경로 모양만 그립니다.
 * 지도를 끈 상태에서는 지도 라이브러리를 아예 만들지 않으므로 인터넷 요청이 생기지 않습니다.
 */
class MapPanel(private val ctx: Context, container: FrameLayout, val useMap: Boolean) {
    private val density = ctx.resources.displayMetrics.density
    private var map: MapView? = null
    private var routeView: RouteView? = null
    private val lines = ArrayList<Pair<Polyline, ArrayList<GeoPoint>>>()
    private val casings = ArrayList<Polyline>()
    private var lastMotion: Motion? = null
    private var rendered = 0
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var touchedAt = 0L
    private var points: List<TrackPoint> = emptyList()

    init {
        container.removeAllViews()
        container.clipToOutline = true
        if (useMap) {
            configure(ctx)
            map = MapView(ctx).also { m ->
                m.setTileSource(TileSourceFactory.MAPNIK)
                // 시안처럼 어두운 지도: 기본 지도의 색을 거의 빼고 밝기를 뒤집은 뒤 남색으로 살짝 물들입니다
                val cm = ColorMatrix().apply { setSaturation(0.10f) }
                cm.postConcat(ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )))
                cm.postConcat(ColorMatrix(floatArrayOf(
                    0.62f, 0f, 0f, 0f, 8f,
                    0f, 0.68f, 0f, 0f, 14f,
                    0f, 0f, 0.78f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f,
                )))
                m.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(cm))
                m.overlayManager.tilesOverlay.setLoadingBackgroundColor(0xFF151D25.toInt())
                m.overlayManager.tilesOverlay.setLoadingLineColor(0xFF151D25.toInt())
                m.setMultiTouchControls(true)
                m.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                m.isTilesScaledToDpi = true
                m.minZoomLevel = 4.0
                m.maxZoomLevel = 19.0
                m.controller.setZoom(15.0)
                m.controller.setCenter(GeoPoint(37.5665, 126.9780))
                m.overlays.add(CopyrightOverlay(ctx).apply { setTextSize(10) })
                keepTouches(m)
                container.addView(m, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } else {
            routeView = RouteView(ctx).also {
                container.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    // 스크롤 화면 안에서도 지도를 끌고 확대할 수 있게, 지도를 만지는 동안은 화면 스크롤이 가로채지 않게 합니다
    @SuppressLint("ClickableViewAccessibility")
    private fun keepTouches(m: MapView) {
        m.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                touchedAt = SystemClock.elapsedRealtime()
            }
            false
        }
    }

    fun setEmptyText(t: String) { routeView?.emptyText = t }

    /** 기록이 늘어날 때마다 부릅니다. 이미 그린 점은 다시 그리지 않습니다. follow면 마지막 위치로 지도를 옮깁니다. */
    fun setPoints(p: List<TrackPoint>, follow: Boolean) {
        points = p
        routeView?.setPoints(p)
        val m = map ?: return
        if (p.size < rendered) clearMap(m)
        for (i in rendered until p.size) {
            val gp = GeoPoint(p[i].lat, p[i].lon)
            val mo = p[i].motion
            if (i == 0) { lastMotion = null; continue }
            if (mo != lastMotion || lines.isEmpty()) {
                // 흰 테두리 선을 먼저 깔아 경로가 지도 위에서 또렷하게 보이게 합니다
                val casing = Polyline(m)
                casing.outlinePaint.apply {
                    color = 0xCC0B1016.toInt()
                    strokeWidth = (if (mo == Motion.STILL) 6f else 9.5f) * density
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
                }
                casing.setOnClickListener { _, _, _ -> true }
                val pl = Polyline(m)
                pl.outlinePaint.apply {
                    color = ContextCompat.getColor(ctx, colorOf(mo))
                    strokeWidth = (if (mo == Motion.STILL) 3.5f else 6f) * density
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
                }
                pl.setOnClickListener { _, _, _ -> true }
                val list = arrayListOf(GeoPoint(p[i - 1].lat, p[i - 1].lon))
                lines += pl to list
                casings += casing
                m.overlays.add(0, casing)                 // 테두리는 모든 경로선 아래에
                m.overlays.add(m.overlays.size - 1, pl)   // 저작권 표시는 맨 위에 둡니다
                lastMotion = mo
            }
            val (pl, list) = lines.last()
            list.add(gp); pl.setPoints(list); casings.last().setPoints(list)
        }
        rendered = p.size
        if (p.isNotEmpty()) {
            val first = GeoPoint(p.first().lat, p.first().lon)
            val last = GeoPoint(p.last().lat, p.last().lon)
            if (startMarker == null) startMarker = marker(m, 0xFFFFFFFF.toInt())
            if (endMarker == null) endMarker = marker(m, ContextCompat.getColor(ctx, R.color.ink))
            startMarker!!.position = first
            endMarker!!.position = last
            if (follow && SystemClock.elapsedRealtime() - touchedAt > 10_000) {
                if (m.zoomLevelDouble < 16.0) m.controller.setZoom(17.0)
                m.controller.animateTo(last)
            }
        }
        m.invalidate()
    }

    /** 경로 전체가 보이게 맞춥니다(기록 보기 화면). */
    fun fitAll() {
        val m = map ?: return
        if (points.size < 2) { points.firstOrNull()?.let { center(it.lat, it.lon) }; return }
        val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) })
        val doFit = {
            if (box.latitudeSpan < 1e-4 && box.longitudeSpan < 1e-4) {
                m.controller.setZoom(17.0); m.controller.setCenter(box.centerWithDateLine)
            } else {
                m.zoomToBoundingBox(box.increaseByScale(1.15f), false, (24 * density).toInt())
            }
        }
        if (m.width > 0 && m.height > 0) doFit() else m.addOnFirstLayoutListener { _, _, _, _, _ -> doFit() }
    }

    fun center(lat: Double, lon: Double) {
        map?.controller?.apply { setZoom(16.0); setCenter(GeoPoint(lat, lon)) }
    }

    fun onResume() { map?.onResume() }
    fun onPause() { map?.onPause() }
    fun onDestroy() { map?.onDetach(); map = null }

    private fun clearMap(m: MapView) {
        lines.forEach { m.overlays.remove(it.first) }
        casings.forEach { m.overlays.remove(it) }
        lines.clear(); casings.clear(); rendered = 0; lastMotion = null
    }

    private fun marker(m: MapView, color: Int): Marker {
        val size = (14 * density).toInt()
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(color); setSize(size, size)
            setStroke((3 * density).toInt(), ContextCompat.getColor(ctx, R.color.ink))
        }
        return Marker(m).also {
            it.setIcon(dot)
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.setOnMarkerClickListener { _, _ -> true }
            m.overlays.add(m.overlays.size - 1, it)
        }
    }

    companion object {
        fun colorOf(m: Motion) = when (m) { Motion.RUN -> R.color.run; Motion.WALK -> R.color.walk; Motion.STILL -> R.color.still }

        /** 지도 조각은 앱 전용 캐시 폴더에만 저장하고, OpenStreetMap 정책대로 앱을 밝히는 이름을 붙여 요청합니다. */
        fun configure(ctx: Context) {
            val c = Configuration.getInstance()
            c.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            c.userAgentValue = "RunMate/${BuildConfig.VERSION_NAME} (+https://github.com/sby1234-ai/runmate)"
            c.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            c.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }

        fun clearCache(ctx: Context): Boolean = File(ctx.cacheDir, "osmdroid").deleteRecursively()
    }
}
