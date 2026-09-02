package com.mtzallqmy.agentna.security

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
 * Stores provider API keys encrypted with an AES key that never leaves Android Keystore.
 * Only encrypted blobs are persisted in SharedPreferences.
 */
class SecureApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val configPrefs = context.applicationContext.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)

    fun putApiKey(provider: String, rawKey: String) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            removeApiKey(provider)
            return
        }
        require(key.length <= MAX_KEY_LENGTH) { "API key is unexpectedly long" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(keyName(provider), payload).apply()
    }

    fun getApiKey(provider: String): String? {
        val payload = prefs.getString(keyName(provider), null) ?: return null
        return runCatching {
            val parts = payload.split(':', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasApiKey(provider: String): Boolean = !getApiKey(provider).isNullOrBlank()

    fun removeApiKey(provider: String) {
        prefs.edit().remove(keyName(provider)).apply()
    }

    fun maskedApiKey(provider: String): String {
        val value = getApiKey(provider) ?: return ""
        if (value.length < 8) return "••••••••"
        return value.take(4) + "••••••••" + value.takeLast(4)
    }

    fun setModel(provider: String, model: String) {
        configPrefs.edit().putString("model_${normalize(provider)}", model.trim()).apply()
    }

    fun getModel(provider: String, fallback: String): String =
        configPrefs.getString("model_${normalize(provider)}", null)?.takeIf { it.isNotBlank() } ?: fallback

    private fun keyName(provider: String) = "key_${normalize(provider)}"
    private fun normalize(provider: String) = provider.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "agentna_secure_keys"
        private const val CONFIG_PREFS = "agentna_provider_config"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "agentna_provider_keys_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_KEY_LENGTH = 4096
    }
}
