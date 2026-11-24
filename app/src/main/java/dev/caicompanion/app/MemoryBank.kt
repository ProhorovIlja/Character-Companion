package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

/** Bounded, local lexical memory. No account IDs, network or model dependency. */
object MemoryBank {
    const val MAX_ENTRIES = 96
    const val MAX_PROFILE = 80
    const val MAX_TITLE = 100
    const val MAX_BODY = 2000
    const val MAX_RECALL = 6000
    private const val MAGIC = 0x43434d42 // CCMB
    private val words = Regex("[\\p{L}\\p{N}\\p{M}]+")
    data class Entry(val profile: String, val title: String, val body: String)
    data class Recall(val entries: List<Entry>, val text: String, val omitted: Int)
    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    private fun valid(s: String, max: Int, multiline: Boolean = false): Boolean =
        s.length in 1..max && s.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(s) &&
            s.none { it.isISOControl() && !(multiline && it in "\t\n\r") }
    fun validProfile(s: String) = valid(s, MAX_PROFILE) && fold(s).isNotBlank()
    fun validEntry(e: Entry) = validProfile(e.profile) && valid(e.title, MAX_TITLE) && valid(e.body, MAX_BODY, true)
    private fun key(e: Entry) = fold(e.profile) to fold(e.title)
    private fun validBank(entries: List<Entry>) = entries.size <= MAX_ENTRIES && entries.all(::validEntry) &&
        entries.map(::key).toSet().size == entries.size
    fun profiles(entries: List<Entry>): List<String> = entries.map { it.profile }.distinctBy(::fold).sortedBy(::fold)
    fun forProfile(entries: List<Entry>, profile: String): List<Entry> =
        if (!validProfile(profile)) emptyList() else entries.filter { fold(it.profile) == fold(profile) }
    fun put(entries: List<Entry>, entry: Entry): List<Entry>? {
        if (!validBank(entries) || !validEntry(entry)) return null
        val canonical = entry.copy(profile = entry.profile.trim(), title = entry.title.trim())
        val next = entries.filterNot { key(it) == key(canonical) } + canonical
        return next.takeIf(::validBank)
    }
    fun remove(entries: List<Entry>, entry: Entry): List<Entry>? =
        if (!validBank(entries) || entry !in entries) null else entries.filterNot { it == entry }
    private fun tokens(s: String): Set<String> = words.findAll(fold(s)).map { it.value }.filter { it.length >= 2 }.toSet()
    fun recall(entries: List<Entry>, profile: String, query: String): Recall? {
        if (!validBank(entries) || !validProfile(profile) || !valid(query, 512)) return null
        val terms = tokens(query)
        if (terms.isEmpty()) return null
        val ranked = forProfile(entries, profile).asReversed().map { e ->
            val title = tokens(e.title)
            val body = tokens(e.body)
            e to terms.sumOf { term -> (if (term in title) 3 else 0) + (if (term in body) 1 else 0) }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val selected = mutableListOf<Entry>()
        val out = StringBuilder()
        for ((entry, _) in ranked) {
            val section = (if (out.isEmpty()) "" else "\n\n") + "[" + entry.title + "]\n" + entry.body
            if (out.length + section.length > MAX_RECALL) continue
            out.append(section)
            selected.add(entry)
        }
        return Recall(selected, out.toString(), ranked.size - selected.size)
    }
    fun write(entries: List<Entry>, output: OutputStream) {
        require(validBank(entries))
        val stream = DataOutputStream(output)
        stream.writeInt(MAGIC); stream.writeInt(1); stream.writeInt(entries.size)
        for (e in entries) for (s in listOf(e.profile, e.title, e.body)) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            stream.writeInt(bytes.size); stream.write(bytes)
        }
        stream.flush()
    }
    fun read(input: InputStream): List<Entry> {
        val stream = DataInputStream(input)
        require(stream.readInt() == MAGIC && stream.readInt() == 1)
        val count = stream.readInt()
        require(count in 0..MAX_ENTRIES)
        fun text(max: Int): String {
            val length = stream.readInt()
            require(length in 1..max * 3)
            val bytes = ByteArray(length)
            stream.readFully(bytes)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        val entries = List(count) { Entry(text(MAX_PROFILE), text(MAX_TITLE), text(MAX_BODY)) }
        require(stream.read() == -1 && validBank(entries))
        return entries
    }
}
