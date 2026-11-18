package io.github.radixtrator.resync.notify

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.radixtrator.resync.FadenApp
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.data.Reminder
import io.github.radixtrator.resync.i18n.t
import java.util.concurrent.TimeUnit

/**
 * Post options › Remind me: one job per reminded post, run at its time, which shows a notification that opens the post.
 * No network needed: the post's title and community travel in the job.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val postId = inputData.getString(KEY_POST) ?: return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val subreddit = inputData.getString(KEY_SUBREDDIT).orEmpty()
        (applicationContext as? FadenApp)?.containerOrNull?.reminders?.remove(postId)
        notify(applicationContext, postId, title, subreddit)
        return Result.success()
    }

    companion object {
        private const val TAG = "FadenReminder"
        private const val CHANNEL = "reminders"
        private const val KEY_POST = "post"
        private const val KEY_TITLE = "title"
        private const val KEY_SUBREDDIT = "subreddit"

        private fun workName(postId: String) = "reminder:$postId"

        /** Sets (or moves) [reminder]'s job. */
        fun schedule(context: Context, reminder: Reminder) {
            val delay = (reminder.atMillis - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_POST to reminder.postId, KEY_TITLE to reminder.title, KEY_SUBREDDIT to reminder.subreddit))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(reminder.postId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, postId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(postId))
        }

        private fun notify(context: Context, postId: String, title: String, subreddit: String) {
            if (!Notifications.canNotify(context)) return
            Notifications.channel(context, CHANNEL, t("Reminders"), t("Posts you asked to be reminded of"))
            val id = ("reminder:$postId").hashCode()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_watch)
                .setContentTitle(t("Reminder"))
                .setContentText(title)
                .apply { if (subreddit.isNotEmpty()) setSubText("r/$subreddit") }
                .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                .setContentIntent(Notifications.openPost(context, id, postId))
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }
    }
}
