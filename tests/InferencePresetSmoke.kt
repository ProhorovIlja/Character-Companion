package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    verify(InferencePreset.entries.size == 3)
    verify(InferencePreset.fromKey(null) == InferencePreset.BALANCED)
    verify(InferencePreset.fromKey("invalid") == InferencePreset.BALANCED)
    for (p in InferencePreset.entries) {
        verify(InferencePreset.fromKey(p.key) == p)
        verify(p.outputTokens in 1..1024 && p.outputTokens < p.contextTokens && p.contextTokens in 512..8192)
        verify(p.threads in 1..8)
    }
    val m = ModelAdmission.MIB
    val memory = ModelAdmission.Memory(4000*m, 8192*m, 256*m, false)
    val low = ModelAdmission.evaluate(2048*m, memory, InferencePreset.LOW_MEMORY.contextTokens)
    val balanced = ModelAdmission.evaluate(2048*m, memory, InferencePreset.BALANCED.contextTokens)
    val quality = ModelAdmission.evaluate(2048*m, memory, InferencePreset.QUALITY.contextTokens)
    verify(low.required == balanced.required)
    verify(quality.required == balanced.required + 384*m)
    verify(ModelAdmission.evaluate(2048*m, memory.copy(available=balanced.required), 6144).reason == ModelAdmission.Reason.INSUFFICIENT_HEADROOM)
    verify(ModelAdmission.evaluate(2048*m, memory, 0).reason == ModelAdmission.Reason.INVALID_METRICS)
    println("InferencePreset: $checks checks passed")
}
