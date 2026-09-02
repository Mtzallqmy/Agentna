package com.mtzallqmy.agentna.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.mtzallqmy.agentna.data.model.AutomationModel
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max

object AutomationScheduler {
    private const val TAG_PREFIX = "agentna-automation:"

    fun scheduleNext(context: Context, automation: AutomationModel, replaceExisting: Boolean = true, nowEpochMillis: Long = System.currentTimeMillis()): Long? {
        if (!automation.enabled) { cancel(context, automation.id); return null }
        val next = nextRunEpochMillis(automation.cronExpression, nowEpochMillis) ?: return null
        val tag = tag(automation.id)
        if (replaceExisting) WorkManager.getInstance(context).cancelAllWorkByTag(tag)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val input = Data.Builder().putString(AutomationWorker.KEY_AUTOMATION_ID, automation.id).build()
        val request = OneTimeWorkRequest.Builder(AutomationWorker::class.java)
            .setInitialDelay(max(0L, next - nowEpochMillis), TimeUnit.MILLISECONDS)
            .setConstraints(constraints).setInputData(input).addTag(tag).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$tag:$next", ExistingWorkPolicy.KEEP, request)
        return next
    }

    fun enqueueNow(context: Context, automationId: String) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val input = Data.Builder().putString(AutomationWorker.KEY_AUTOMATION_ID, automationId).putBoolean(AutomationWorker.KEY_MANUAL, true).build()
        val request = OneTimeWorkRequest.Builder(AutomationWorker::class.java).setConstraints(constraints).setInputData(input).addTag(tag(automationId)).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context, automationId: String) { WorkManager.getInstance(context).cancelAllWorkByTag(tag(automationId)) }

    internal fun nextRunEpochMillis(expression: String, nowEpochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val parts = expression.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size != 5 || parts[2] != "*" || parts[3] != "*" || parts[4] != "*") return null
        val minute = parts[0].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    fun describe(expression: String): String {
        val parts = expression.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size != 5 || parts.drop(2).any { it != "*" }) return "Unsupported schedule"
        val minute = parts[0].toIntOrNull() ?: return "Unsupported schedule"
        val hour = parts[1].toIntOrNull() ?: return "Unsupported schedule"
        if (minute !in 0..59 || hour !in 0..23) return "Unsupported schedule"
        return "Daily %02d:%02d".format(hour, minute)
    }

    private fun tag(id: String) = "$TAG_PREFIX$id"
}
