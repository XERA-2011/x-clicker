package dev.xera.xclicker

import android.app.Application
import dev.xera.xclicker.data.AppContainer

class XClickerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
