package dev.caicompanion.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets

/** Only user-selected normalized rules; stored atomically in private application storage. */
class ImportedRuleStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "manual-domain-rules-v1.txt"))

    @Synchronized fun load(): RuleListParser.Rules = try {
        file.openRead().use { input ->
            val text = RuleListParser.utf8Reader(input).use { reader ->
                BoundedText.read(reader, RuleListParser.MAX_CHARACTERS)
            }
            // BoundedText returns null for oversized or unreadable input.
            text?.let(RuleListParser::parse) ?: RuleListParser.Rules()
        }
    } catch (_: Exception) { RuleListParser.Rules() }

    /** Replace the previous list only after validating the new list in full. */
    @Synchronized fun save(rules: RuleListParser.Rules) {
        require(rules.size in 1..RuleListParser.MAX_DOMAINS)
        val bytes = rules.canonicalText().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= RuleListParser.MAX_CHARACTERS)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized fun clear() = file.delete()
}
