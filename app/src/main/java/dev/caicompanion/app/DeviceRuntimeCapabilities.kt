package dev.caicompanion.app

/** Explicit runtime snapshot. No identifiers, persistent history or remote telemetry. */
object DeviceRuntimeCapabilities {
    data class Snapshot(
        val renderer: String? = null,
        val totalRamMiB: Long? = null,
        val appHeapClassMiB: Int? = null,
        val lowRamDevice: Boolean? = null,
        val powerSave: Boolean? = null,
        val thermalStatus: Int? = null,
        val localSpeechAvailable: Boolean? = null
    )

    private fun state(value: Boolean?): String = when (value) {
        true -> "yes"
        false -> "no"
        null -> "unknown"
    }

    fun thermalLabel(status: Int?): String = when (status) {
        0 -> "none"
        1 -> "light"
        2 -> "moderate"
        3 -> "severe"
        4 -> "critical"
        5 -> "emergency"
        6 -> "shutdown"
        else -> "unknown"
    }

    fun format(snapshot: Snapshot): String = buildString {
        appendLine("Session renderer: ${when (snapshot.renderer) {
            "legacy" -> "Android WebView"
            "gecko" -> "GeckoView"
            "browser" -> "external browser"
            else -> "unknown"
        }}")
        val ram = snapshot.totalRamMiB?.takeIf { it in 128L..262144L }
        val heap = snapshot.appHeapClassMiB?.takeIf { it in 16..65536 }
        appendLine("OS-reported total RAM: ${ram?.let { "$it MiB" } ?: "unknown"}")
        appendLine("App heap class: ${heap?.let { "$it MiB" } ?: "unknown"}")
        appendLine("Low-RAM device class: ${state(snapshot.lowRamDevice)}")
        appendLine("Battery saver active: ${state(snapshot.powerSave)}")
        appendLine("Thermal status: ${thermalLabel(snapshot.thermalStatus)}")
        appendLine("On-device speech service: ${state(snapshot.localSpeechAvailable)} (model and recognition untested)")
        append("Snapshot only: heap class is not actual process or renderer memory usage.")
    }
}
