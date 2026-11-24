package dev.caicompanion.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** All mutations use a fresh read under one process lock. */
object MemoryBankStore {
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "memory-bank-v1.bin"))
    @Synchronized fun load(context: Context): List<MemoryBank.Entry> {
        val target = file(context)
        val input = try { target.openRead() } catch (e: FileNotFoundException) {
            if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists() &&
                !File(target.baseFile.path + ".new").exists()) return emptyList()
            throw e
        }
        return input.use { MemoryBank.read(it.buffered()) }
    }
    private fun save(context: Context, entries: List<MemoryBank.Entry>) {
        val target = file(context)
        val output = target.startWrite()
        try {
            MemoryBank.write(entries, output.buffered())
            target.finishWrite(output)
        } catch (e: Exception) {
            target.failWrite(output)
            throw e
        }
        check(load(context) == entries)
    }
    @Synchronized fun put(context: Context, expected: List<MemoryBank.Entry>, entry: MemoryBank.Entry) {
        val current = load(context)
        check(current == expected)
        save(context, MemoryBank.put(current, entry) ?: error("Invalid or full memory bank"))
    }
    @Synchronized fun merge(context: Context, expected: List<MemoryBank.Entry>,
                            incoming: List<MemoryBank.Entry>): MemoryBankBackup.Merge {
        val current = load(context)
        check(current == expected)
        val plan = MemoryBankBackup.merge(current, incoming) ?: error("Invalid or full memory bank")
        if (plan.added > 0) save(context, plan.entries)
        return plan
    }
    @Synchronized fun remove(context: Context, entry: MemoryBank.Entry) {
        save(context, MemoryBank.remove(load(context), entry) ?: error("Memory changed"))
    }
}
