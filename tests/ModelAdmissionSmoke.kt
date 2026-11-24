package dev.caicompanion.app

fun main() {
    val m = ModelAdmission.MIB
    val base = ModelAdmission.Memory(4096*m, 8192*m, 256*m, false)
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun eval(size: Long, mem: ModelAdmission.Memory = base) = ModelAdmission.evaluate(size, mem)
    val allowed = eval(2048*m)
    verify(allowed.allowed && allowed.required == 3328*m)
    verify(eval(2048*m, base.copy(available=3328*m)).allowed)
    verify(eval(2048*m, base.copy(available=3328*m-1)).reason == ModelAdmission.Reason.INSUFFICIENT_HEADROOM)
    verify(eval(4096*m).reason == ModelAdmission.Reason.INSUFFICIENT_HEADROOM)
    verify(eval(1, base.copy(low=true)).reason == ModelAdmission.Reason.LOW_MEMORY)
    verify(eval(1, base.copy(available=base.threshold)).reason == ModelAdmission.Reason.LOW_MEMORY)
    verify(eval(2048*m, base.copy(threshold=1024*m)).required == 3840*m)
    verify(eval(Long.MAX_VALUE).reason == ModelAdmission.Reason.INSUFFICIENT_HEADROOM)
    verify(eval(Long.MAX_VALUE).required == Long.MAX_VALUE)
    for (mem in listOf(base.copy(total=0), base.copy(available=-1), base.copy(available=base.total+1), base.copy(threshold=-1), base.copy(threshold=base.total+1))) {
        verify(eval(1, mem).reason == ModelAdmission.Reason.INVALID_METRICS)
    }
    verify(eval(0).reason == ModelAdmission.Reason.INVALID_METRICS)
    verify(eval(-1).reason == ModelAdmission.Reason.INVALID_METRICS)
    ModelAdmission.requireAllowed(2048*m, base)
    verify(runCatching { ModelAdmission.requireAllowed(4096*m, base) }.exceptionOrNull() is ModelAdmission.Denied)
    println("ModelAdmission: $checks checks passed")
}
