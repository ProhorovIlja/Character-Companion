package dev.caicompanion.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Experimental Android adapter. Never export the Keystore wrapping key. */
object AndroidRecoveryVault {
    fun open(context: Context, account: String): RecoveryVault {
        NativeConversation.canonicalId(account)
        val alias = "mandy-continuity-wrap-v1-" + account
        val root = context.applicationContext.noBackupFilesDir.toPath().resolve("mandy-recovery-v1")
        return RecoveryVault(root, account) { create ->
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) {
                store.getKey(alias, null) as? SecretKey ?: error("Invalid wrapping key")
            } else {
                check(create) { "Wrapping key unavailable; recovery required" }
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build())
                    generateKey()
                }
            }
        }
    }
}
