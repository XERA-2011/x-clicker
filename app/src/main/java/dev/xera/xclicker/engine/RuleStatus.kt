package dev.xera.xclicker.engine

sealed class ResetMatchType(val value: String) {
    data object Activity : ResetMatchType("activity")
    data object Match : ResetMatchType("match")
    data object App : ResetMatchType("app")

    companion object {
        val allSubObject by lazy { listOf(Activity, Match, App) }
    }
}

sealed class RuleStatus(val name: String) {
    data object StatusOk : RuleStatus("ok")
    data object Status1 : RuleStatus("达到最大执行次数")
    data object Status2 : RuleStatus("需要提前触发某个规则")
    data object Status3 : RuleStatus("处于匹配延迟")
    data object Status4 : RuleStatus("超出匹配时间")
    data object Status5 : RuleStatus("处于冷却时间")
    data object Status6 : RuleStatus("处于触发延迟")

    val ok: Boolean
        get() = this === StatusOk

    val alive: Boolean
        get() = this !== Status1 && this !== Status2 && this !== Status4
}
