package io.github.sby1234ai.runmate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.sby1234ai.runmate.logic.Activity

/** 휴대폰 동작 인식이 걷기·뛰기·멈춤으로 바뀌었다고 알려 줄 때 받습니다. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (e in result.transitionEvents) {
            if (e.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) continue
            val a = when (e.activityType) {
                DetectedActivity.RUNNING -> Activity.RUNNING
                DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> Activity.WALKING
                DetectedActivity.STILL -> Activity.STILL
                else -> Activity.OTHER
            }
            RunRecorder.onActivity(a, System.currentTimeMillis())
        }
    }
}
