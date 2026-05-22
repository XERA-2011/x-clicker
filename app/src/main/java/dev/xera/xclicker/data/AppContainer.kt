package dev.xera.xclicker.data

import android.content.Context

class AppContainer(context: Context) {
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }
    val ruleManager: RuleManager by lazy { RuleManager(context) }
}
