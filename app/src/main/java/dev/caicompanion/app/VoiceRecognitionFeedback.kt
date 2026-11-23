package dev.caicompanion.app

/**
 * Platform-independent mapping of Android SpeechRecognizer error codes.
 * The numeric constants match the public Android SDK: SpeechRecognizer.ERROR_*.
 * It contains neither transcript text nor microphone buffers.
 */
enum class VoiceIssue {
    NO_SPEECH, LANGUAGE_NOT_SUPPORTED, MODEL_MISSING, BUSY,
    PERMISSION, MICROPHONE, NETWORK_OR_SERVICE, OTHER
}

object VoiceRecognitionFeedback {
    fun issue(code: Int): VoiceIssue = when (code) {
        6, 7 -> VoiceIssue.NO_SPEECH // ERROR_SPEECH_TIMEOUT / ERROR_NO_MATCH
        12 -> VoiceIssue.LANGUAGE_NOT_SUPPORTED
        13 -> VoiceIssue.MODEL_MISSING // Language model not installed/available
        8, 10 -> VoiceIssue.BUSY // Busy / rate limited
        9 -> VoiceIssue.PERMISSION
        3 -> VoiceIssue.MICROPHONE
        1, 2, 4, 11 -> VoiceIssue.NETWORK_OR_SERVICE
        else -> VoiceIssue.OTHER
    }
}
