package dev.xera.xclicker.data.model

data class PopupRule(
    val id: String,
    val action: String,
    val delay: Long = 0L,
    val times: Int = 1
)

data class AppRuleSet(
    val packageHash: String,
    val popupRules: List<PopupRule>,
    val lttService: Boolean = true,
    val clickWay: Int = 1
)
