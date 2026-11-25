package dev.caicompanion.app

/** Provisional CPU inference presets; benchmark on the actual device before tuning. */
enum class InferencePreset(val key: String, val label: String, val contextTokens: Int, val outputTokens: Int, val threads: Int) {
    LOW_MEMORY("low", "Low memory", 2048, 256, 2),
    BALANCED("balanced", "Balanced", 4096, 512, 4),
    QUALITY("quality", "Longer context", 6144, 768, 6);

    /** Client text allowance; native token fitting remains authoritative. */
    val contextUnits: Int get() = if (this == QUALITY) 24000 else 12000

    companion object {
        fun fromKey(key: String?): InferencePreset = entries.firstOrNull { it.key == key } ?: BALANCED
    }
}
