package com.paisatrail.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paisatrail.MainActivity
import com.paisatrail.R
import com.paisatrail.data.local.CommitmentEntity
import com.paisatrail.data.local.PlannerDao
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.domain.model.CommitmentType
import com.paisatrail.domain.util.DueDates
import com.paisatrail.domain.util.Money
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Lets the WorkManager-created worker reach Hilt singletons without a custom WorkerFactory. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun plannerDao(): PlannerDao
    fun settingsStore(): SettingsStore
}

/** Runs about once a day and posts a notification for anything due today or tomorrow. */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        if (!entryPoint.settingsStore().current().remindersEnabled) return Result.success()

        val today = LocalDate.now()
        for (commitment in entryPoint.plannerDao().getCommitments()) {
            val days = DueDates.daysUntil(commitment.dueDay, today)
            if (days in 0..1) ReminderNotifications.notifyDue(applicationContext, commitment, days)
        }
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "due_date_reminders"

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, firstRun).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

object ReminderNotifications {
    const val CHANNEL_ID = "due_reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.channel_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun notifyDue(context: Context, commitment: CommitmentEntity, daysUntilDue: Int) {
        if (!canNotify(context)) return
        val whenText = if (daysUntilDue == 0) "today" else "tomorrow"
        val kind = when (commitment.type) {
            CommitmentType.SUBSCRIPTION -> "renews"
            CommitmentType.EMI -> "EMI is due"
            CommitmentType.CREDIT_CARD -> "bill is due"
        }
        val amount = if (commitment.amountPaise > 0) " · ${Money.format(commitment.amountPaise)}" else ""
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${commitment.name} $kind $whenText")
            .setContentText("Due on day ${commitment.dueDay} of the month$amount")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(commitment.id.toInt(), notification)
    }
}
