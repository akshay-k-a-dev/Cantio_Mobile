package com.appplayer.music.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.appplayer.music.MainActivity
import com.appplayer.music.R
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class BlendInviteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun musicRepository(): MusicRepository
        fun tokenManager(): TokenManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val musicRepository = entryPoint.musicRepository()
        val tokenManager = entryPoint.tokenManager()

        if (!tokenManager.isLoggedIn()) {
            return Result.success()
        }

        when (val result = musicRepository.getBlendInvites()) {
            is ApiResult.Success<*> -> {
                val invites = result.data as? List<*> ?: emptyList<Any>()
                val pendingInvites = invites.filterIsInstance<com.appplayer.music.data.api.models.BlendInvite>()
                    .filter { it.status.uppercase() == "PENDING" }

                if (pendingInvites.isNotEmpty()) {
                    val sharedPrefs = applicationContext.getSharedPreferences("blend_invite_notif_prefs", Context.MODE_PRIVATE)
                    val notifiedIds = sharedPrefs.getStringSet("notified_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                    
                    var newNotifCount = 0
                    pendingInvites.forEach { invite ->
                        if (invite.id !in notifiedIds) {
                            showInviteNotification(invite)
                            notifiedIds.add(invite.id)
                            newNotifCount++
                        }
                    }

                    if (newNotifCount > 0) {
                        sharedPrefs.edit().putStringSet("notified_ids", notifiedIds).apply()
                    }
                }
            }
            else -> {
                // Ignore error, retry on next cycle
            }
        }

        return Result.success()
    }

    private fun showInviteNotification(invite: com.appplayer.music.data.api.models.BlendInvite) {
        val senderName = invite.sender?.name ?: invite.sender?.username ?: "Someone"
        val channelId = "blend_invites"
        
        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Blend Invites"
            val descriptionText = "Notifications for blend invitations"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            invite.id.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("New Blend Invitation")
            .setContentText("$senderName invited you to create a music Blend!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.notify(invite.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+ yet
        }
    }
}
