package com.appplayer.music

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

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
    }
}
