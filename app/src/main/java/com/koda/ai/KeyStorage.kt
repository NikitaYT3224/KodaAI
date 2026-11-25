package com.koda.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Хранит API-ключ в зашифрованном виде.
 * Сам ключ шифрования лежит в Android Keystore и не покидает устройство.
 */
class KeyStorage(context: Context) {

    private val prefs = context.getSharedPreferences("koda_auth", Context.MODE_PRIVATE)

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /** Возвращает ключ или null, если его нет или он не расшифровывается. */
    fun load(): String? {
        val data = prefs.getString(KEY_DATA, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // ключ повреждён или Keystore был сброшен — просим войти заново
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "koda_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_DATA = "data"
        const val KEY_IV = "iv"
    }
}
