package com.andrewwin.sumup.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretEncryptionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    data class SyncEncryptionSession(
        val saltBase64: String,
        private val secretKey: SecretKey
    ) {
        fun encrypt(plainText: String): String {
            if (plainText.isBlank()) return plainText
            val iv = ByteArray(SYNC_IV_SIZE).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance(SYNC_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            return buildString {
                append(SYNC_SESSION_PREFIX)
                append(Base64.encodeToString(iv, Base64.NO_WRAP))
                append(':')
                append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        }

        companion object {
            private val secureRandom = SecureRandom()
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun encryptLocal(plainText: String): String {
        if (plainText.isBlank()) return plainText
        if (isLocallyEncrypted(plainText)) return plainText

        val cipher = Cipher.getInstance(LOCAL_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateLocalKey())
        val iv = cipher.iv ?: error("Keystore did not return an IV for local encryption.")
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            append(LOCAL_PREFIX)
            append(encode(iv))
            append(':')
            append(encode(encrypted))
        }
    }

    fun decryptLocal(value: String): String {
        if (!isLocallyEncrypted(value)) return value
        val payload = value.removePrefix(LOCAL_PREFIX)
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2) { "Invalid local encrypted payload." }
        val iv = decode(parts[0])
        val ciphertext = decode(parts[1])
        val cipher = Cipher.getInstance(LOCAL_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateLocalKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun isLocallyEncrypted(value: String): Boolean = value.startsWith(LOCAL_PREFIX)

    fun encryptForSync(plainText: String, passphrase: String): String {
        if (plainText.isBlank()) return plainText
        val salt = randomBytes(SYNC_SALT_SIZE)
        val iv = randomBytes(SYNC_IV_SIZE)
        val cipher = Cipher.getInstance(SYNC_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveSyncKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            append(SYNC_PREFIX)
            append(encode(salt))
            append(':')
            append(encode(iv))
            append(':')
            append(encode(encrypted))
        }
    }

    fun decryptFromSync(payload: String, passphrase: String): String {
        if (!isSyncEncrypted(payload)) return payload
        if (payload.startsWith(SYNC_SESSION_PREFIX)) {
            error("Sync session ciphertext requires a pre-derived session key.")
        }
        val content = payload.removePrefix(SYNC_PREFIX)
        val parts = content.split(':', limit = 3)
        require(parts.size == 3) { "Invalid sync encrypted payload." }
        val salt = decode(parts[0])
        val iv = decode(parts[1])
        val ciphertext = decode(parts[2])
        val cipher = Cipher.getInstance(SYNC_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveSyncKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun isSyncEncrypted(value: String): Boolean = value.startsWith(SYNC_PREFIX)

    fun createSyncEncryptionSession(passphrase: String): SyncEncryptionSession {
        require(passphrase.isNotBlank()) { "Sync passphrase is missing." }
        val salt = randomBytes(SYNC_SALT_SIZE)
        val saltBase64 = encode(salt)
        return SyncEncryptionSession(
            saltBase64 = saltBase64,
            secretKey = deriveSyncKey(passphrase, salt)
        )
    }

    fun decryptFromSyncSession(payload: String, passphrase: String, saltBase64: String): String {
        if (!payload.startsWith(SYNC_SESSION_PREFIX)) return decryptFromSync(payload, passphrase)
        val content = payload.removePrefix(SYNC_SESSION_PREFIX)
        val parts = content.split(':', limit = 2)
        require(parts.size == 2) { "Invalid sync session encrypted payload." }
        val salt = decode(saltBase64)
        val iv = decode(parts[0])
        val ciphertext = decode(parts[1])
        val cipher = Cipher.getInstance(SYNC_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveSyncKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun hasSyncPassphrase(): Boolean = prefs.contains(KEY_SYNC_PASSPHRASE)

    fun getSyncPassphraseOrNull(): String? {
        val stored = prefs.getString(KEY_SYNC_PASSPHRASE, null) ?: return null
        return runCatching { decryptLocal(stored) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun setSyncPassphrase(passphrase: String) {
        prefs.edit()
            .putString(KEY_SYNC_PASSPHRASE, encryptLocal(passphrase))
            .apply()
    }

    fun clearSyncPassphrase() {
        prefs.edit().remove(KEY_SYNC_PASSPHRASE).apply()
    }

    private fun deriveSyncKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, KEY_ALGORITHM)
    }

    private fun getOrCreateLocalKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(LOCAL_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val builder = KeyGenParameterSpec.Builder(
            LOCAL_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(PBKDF2_KEY_BITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val PREFS_NAME = "secret_security_prefs"
        private const val KEY_SYNC_PASSPHRASE = "sync_passphrase"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val LOCAL_KEY_ALIAS = "sumup_local_secret_key"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val LOCAL_IV_SIZE = 12
        private const val SYNC_IV_SIZE = 12
        private const val SYNC_SALT_SIZE = 16

        private const val LOCAL_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SYNC_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val LOCAL_PREFIX = "local:v1:"
        private const val SYNC_PREFIX = "sync:v1:"
        private const val SYNC_SESSION_PREFIX = "sync:v2:"
    }
}
