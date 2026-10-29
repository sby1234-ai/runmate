package io.github.sby1234ai.runmate

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.sby1234ai.runmate.logic.Geo
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.TrackPoint
import java.time.Instant

/**
 * 헬스 커넥트 연결(읽기만). 갤럭시 워치 등은 삼성 헬스를 거쳐 심박·운동을 헬스 커넥트에 넘깁니다.
 * - 휴대폰으로 뛴 기록: 그 시간대의 워치 심박을 읽어 붙임
 * - 워치로만 뛴 운동: 경로·시간을 런메이트 기록으로 가져옴(걷기·뛰기는 속도로 나눔)
 */
object HealthLink {
    /** 헬스 커넥트 권한 화면에서 허용받을 것 */
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        "android.permission.health.READ_EXERCISE_ROUTES",
    )

    /** 이 휴대폰에서 헬스 커넥트를 쓸 수 있는지 */
    fun available(ctx: Context): Boolean =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Boolean =
        available(ctx) && client(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS.take(2))

    /** [from, to] 사이 심박: (시각 ms, bpm) 시간순 */
    suspend fun heart(ctx: Context, from: Long, to: Long): List<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        var token: String? = null
        do {
            val res = client(ctx).readRecords(ReadRecordsRequest(HeartRateRecord::class,
                TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)), pageToken = token))
            res.records.forEach { r -> r.samples.forEach { s -> out += s.time.toEpochMilli() to s.beatsPerMinute.toInt() } }
            token = res.pageToken
        } while (token != null)
        return out.filter { it.first in from..to }.sortedBy { it.first }
    }

    /** 워치로 뛴(또는 걸은) 운동 중 경로가 있는 것 — 런메이트가 이미 가진 시각과 겹치는 것은 뺌 */
    suspend fun watchRuns(ctx: Context, sinceDays: Int, haveIds: Set<Long>): List<Run> {
        val now = Instant.now()
        val res = client(ctx).readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,
            TimeRangeFilter.between(now.minusSeconds(sinceDays * 86_400L), now)))
        val types = setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        return res.records
            .filter { it.exerciseType in types && it.metadata.dataOrigin.packageName != ctx.packageName }
            .filter { s -> haveIds.none { kotlin.math.abs(it - s.startTime.toEpochMilli()) < 60_000 } }
            .mapNotNull { s ->
                val route = (s.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute?.route ?: return@mapNotNull null
                if (route.size < 2) return@mapNotNull null
                // 경로 점 → 런메이트 점. 걷기·뛰기는 앞 점과의 속도로(기준 2.2 m/s ≈ 1km 7분 35초)
                var prev: TrackPoint? = null
                val pts = route.map { p ->
                    val t = p.time.toEpochMilli()
                    val sp = prev?.let { q ->
                        val dt = (t - q.time) / 1000.0
                        if (dt > 0) Geo.distance(q.lat, q.lon, p.latitude, p.longitude) / dt else null
                    }
                    val motion = when { sp == null -> Motion.WALK; sp < 0.6 -> Motion.STILL; sp >= 2.2 -> Motion.RUN; else -> Motion.WALK }
                    TrackPoint(t, p.latitude, p.longitude, sp?.toFloat(), p.horizontalAccuracy?.inMeters?.toFloat() ?: 10f, motion)
                        .also { prev = it }
                }
                Run(s.startTime.toEpochMilli(), pts)
            }
    }
}
