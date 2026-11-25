package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    verify(TurnBudget.availableForMessage(8000) == 4000)
    verify(TurnBudget.availableForMessage(1) == 11999)
    verify(TurnBudget.availableForMessage(8000, 24000) == 16000)
    verify(TurnBudget.availableForMessage(1, 64000) == 16000)
    verify(TurnBudget.availableForMessage(8000, 256) == 0)
    verify(TurnBudget.fits(8000, 4000))
    verify(!TurnBudget.fits(8000, 4001))
    verify(!TurnBudget.fits(1, 16000))
    verify(!TurnBudget.fits(1, 0))
    verify(TurnBudget.fits(8000, 16000, 24000))
    verify(!TurnBudget.fits(8000, 1, 256))
    verify(TurnBudget.fits("Модель 🌙".length, 11991))
    for (invalid in listOf(0, 8001, -1)) verify(runCatching { TurnBudget.availableForMessage(invalid) }.isFailure)
    for (invalid in listOf(-1, 16001)) verify(runCatching { TurnBudget.fits(1, invalid) }.isFailure)
    for (invalid in listOf(255, 64001)) verify(runCatching { TurnBudget.availableForMessage(1, invalid) }.isFailure)
    println("PASS: $checks turn budget assertions")
}
