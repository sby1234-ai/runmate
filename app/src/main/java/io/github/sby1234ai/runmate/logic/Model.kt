package io.github.sby1234ai.runmate.logic

/** 한 지점의 기록. 시간은 기기 시계(epoch ms), 속도는 m/s(모르면 null). */
data class TrackPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speed: Float?,
    val accuracy: Float,
    val motion: Motion,
)

enum class Motion(val label: String) { STILL("멈춤"), WALK("걷기"), RUN("뛰기") }

/** 휴대폰 동작 인식이 알려 준 상태. */
enum class Activity { STILL, WALKING, RUNNING, OTHER }

data class Run(
    val id: Long,          // 시작 시각(epoch ms)
    val points: List<TrackPoint>,
)
