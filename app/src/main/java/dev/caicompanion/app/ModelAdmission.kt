package dev.caicompanion.app

import java.io.IOException

/** Conservative foreground CPU-model admission, not a prediction of actual peak RAM. */
object ModelAdmission {
    const val MIB = 1024L * 1024
    const val MIN_SYSTEM_RESERVE = 512 * MIB
    const val WORKSPACE_ALLOWANCE = 768 * MIB
    data class Memory(val available: Long, val total: Long, val threshold: Long, val low: Boolean)
    enum class Reason { ALLOWED, INVALID_METRICS, LOW_MEMORY, INSUFFICIENT_HEADROOM }
    data class Decision(val reason: Reason, val required: Long, val available: Long) {
        val allowed: Boolean get() = reason == Reason.ALLOWED
    }
    class Denied(val decision: Decision) : IOException("Local model load refused: " + decision.reason.name)
    fun evaluate(modelBytes: Long, memory: Memory): Decision {
        if (modelBytes <= 0 || memory.total <= 0 || memory.available !in 0..memory.total || memory.threshold !in 0..memory.total)
            return Decision(Reason.INVALID_METRICS, 0, memory.available)
        val reserve = maxOf(MIN_SYSTEM_RESERVE, memory.threshold)
        val required = try { Math.addExact(Math.addExact(modelBytes, WORKSPACE_ALLOWANCE), reserve) }
            catch (_: ArithmeticException) { return Decision(Reason.INSUFFICIENT_HEADROOM, Long.MAX_VALUE, memory.available) }
        val reason = when {
            memory.low || memory.available <= memory.threshold -> Reason.LOW_MEMORY
            memory.available < required -> Reason.INSUFFICIENT_HEADROOM
            else -> Reason.ALLOWED
        }
        return Decision(reason, required, memory.available)
    }
    fun requireAllowed(modelBytes: Long, memory: Memory) {
        val decision = evaluate(modelBytes, memory)
        if (!decision.allowed) throw Denied(decision)
    }
}
