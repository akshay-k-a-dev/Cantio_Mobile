package com.appplayer.music

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.appplayer.music.utils.BlendInviteWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Init logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Init BotGuardTokenGenerator
        com.appplayer.music.utils.potoken.BotGuardTokenGenerator.initialize(this)

        scheduleBlendInviteWorker()
    }

    private fun scheduleBlendInviteWorker() {
        val inviteWorkRequest = PeriodicWorkRequestBuilder<BlendInviteWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BlendInviteWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            inviteWorkRequest
        )
    }
}
