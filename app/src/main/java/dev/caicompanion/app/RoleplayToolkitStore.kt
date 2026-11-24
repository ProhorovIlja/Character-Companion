package dev.caicompanion.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** Explicit user saves only; separate from browser data and excluded from OS backup. */
object RoleplayToolkitStore {
    private fun target(context: Context, slot: RoleplayToolkit.Slot): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, "roleplay-" + slot.key + "-v1.bin"))

    @Synchronized fun load(context: Context, slot: RoleplayToolkit.Slot): String {
        val file = target(context, slot)
        val input = try { file.openRead() } catch (e: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() &&
                !File(file.baseFile.path + ".new").exists()) return ""
            throw e
        }
        return input.use { RoleplayToolkit.read(slot, it) }
    }

    @Synchronized fun save(context: Context, slot: RoleplayToolkit.Slot, text: String) {
        require(RoleplayToolkit.valid(text))
        val file = target(context, slot)
        val output = file.startWrite()
        try {
            RoleplayToolkit.write(slot, text, output)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
        check(load(context, slot) == text)
    }

    @Synchronized fun clear(context: Context, slot: RoleplayToolkit.Slot) {
        val file = target(context, slot)
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() &&
            !File(file.baseFile.path + ".new").exists())
    }
}
