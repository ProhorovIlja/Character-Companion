package dev.caicompanion.app

/** Client text budget, measured in UTF-16 units, not model tokens. */
object TurnBudget {
    const val DEFAULT_CONTEXT_UNITS = 12000
    const val MAX_IDENTITY_UNITS = 8000
    const val MAX_MESSAGE_UNITS = 16000

    fun availableForMessage(identityUnits: Int, contextUnits: Int = DEFAULT_CONTEXT_UNITS): Int {
        require(identityUnits in 1..MAX_IDENTITY_UNITS && contextUnits in 256..64000)
        return (contextUnits - identityUnits).coerceIn(0, MAX_MESSAGE_UNITS)
    }

    fun fits(identityUnits: Int, messageUnits: Int, contextUnits: Int = DEFAULT_CONTEXT_UNITS): Boolean {
        require(messageUnits in 0..MAX_MESSAGE_UNITS)
        return messageUnits > 0 && messageUnits <= availableForMessage(identityUnits, contextUnits)
    }
}
