package ru.wizand.powerwatchdog.data.model

data class StatusItem(
    val title: String,
    var status: StatusState = StatusState.UNKNOWN,
    val fixAction: (() -> Unit)? = null
)

enum class StatusState {
    UNKNOWN, CHECKING, OK, WARNING, ERROR
}