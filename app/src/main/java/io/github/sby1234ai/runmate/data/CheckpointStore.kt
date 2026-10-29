package io.github.sby1234ai.runmate.data

import android.content.Context
import io.github.sby1234ai.runmate.logic.Checkpoint
import java.io.File

/** 달리는 중 기록의 중간 저장(앱 전용 폴더). 기록을 끝내면 지웁니다. */
object CheckpointStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "current_run.json")

    /** 기록이 아직 진행 중일 때만 씁니다(끝난 뒤 늦게 도착한 저장이 지운 파일을 되살리지 않게). */
    @Synchronized
    fun saveIfRecording(ctx: Context, c: Checkpoint, stillRecording: () -> Boolean) {
        if (stillRecording()) save(ctx, c)
    }

    @Synchronized
    fun save(ctx: Context, c: Checkpoint) {
        val tmp = File(ctx.filesDir, "current_run.json.tmp")
        tmp.writeText(c.toJson())
        tmp.renameTo(file(ctx))
    }

    fun load(ctx: Context): Checkpoint? = file(ctx).takeIf { it.exists() }?.let { Checkpoint.fromJson(it.readText()) }
    fun exists(ctx: Context) = file(ctx).exists()
    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete() }
}
