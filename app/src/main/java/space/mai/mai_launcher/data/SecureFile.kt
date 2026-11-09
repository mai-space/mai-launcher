package space.mai.mai_launcher.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Kleine verschlüsselte Datei (AES-256-GCM, Schlüssel im Android Keystore).
 * Format: "MAIL" (4 Byte) · Version 1 (1 Byte) · IV (12 Byte) · Chiffrat.
 * Format nie ändern; neue Formate nur mit neuer Versionsnummer.
 * Alias und Dateiname nie ändern, sonst ist der Inhalt nicht mehr lesbar.
 */
class SecureFile(private val file: File, private val keyAlias: String) {

    fun read(): ByteArray? {
        if (!file.exists()) return null
        return runCatching {
            val all = file.readBytes()
            require(all.size > HEADER.size + 1 + IV_LEN)
            require(all.copyOfRange(0, 4).contentEquals(HEADER))
            require(all[4] == VERSION)
            val iv = all.copyOfRange(5, 5 + IV_LEN)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(all, 5 + IV_LEN, all.size - 5 - IV_LEN)
        }.getOrNull()
    }

    fun write(plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(HEADER); out.write(byteArrayOf(VERSION)); out.write(iv); out.write(body)
        }
        // Atomar ersetzen, damit ein Absturz nie eine halbe Datei hinterlässt.
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        val HEADER = byteArrayOf('M'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), 'L'.code.toByte())
        const val VERSION: Byte = 1
        const val IV_LEN = 12
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
