package org.fisabilillah.app.session

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.fisabilillah.core.auth.SessionStore
import org.fisabilillah.core.auth.StoredSession

/**
 * The refresh token, at rest.
 *
 * What is being protected here is a bearer credential: anybody holding it is signed in as
 * this member until it is revoked, and on this platform that means reading her
 * conversations, her wali's contact details, and the introduction she has told nobody
 * about. Preferences on their own are readable by anything that gets root or an ADB backup
 * on an unlocked device, which is not a hypothetical on shared or second-hand phones.
 *
 * So the value is encrypted with AES-256-GCM under a key that is generated inside the
 * Android keystore and never leaves it. This build does not require user authentication to
 * use the key — demanding a fingerprint on every cold start would be the wrong trade for an
 * app people open to check whether a lift was arranged — but it does mean the ciphertext is
 * worthless off the device it was written on.
 *
 * No third-party crypto library, deliberately: this is ninety lines of platform API, and
 * every dependency added to the client is another thing that has to be trusted with exactly
 * this secret.
 */
internal class KeystoreSessionStore(context: Context) : SessionStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): StoredSession? {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val plain = decrypt(payload) ?: run {
            // The key is gone (app data cleared, device restored from a backup, or the
            // keystore rotated after a lock-screen change). The stored blob can never be
            // read again, so it is dropped rather than left to fail on every start.
            clear()
            return null
        }
        val parts = plain.split(SEPARATOR)
        if (parts.size != 3) {
            clear()
            return null
        }
        return StoredSession(refreshToken = parts[0], userId = parts[1], email = parts[2])
    }

    override fun save(session: StoredSession) {
        // The separator cannot occur in a JWT, a UUID or an address, but a corrupted value
        // must not be able to smuggle one field into another.
        require(SEPARATOR !in session.refreshToken && SEPARATOR !in session.userId) {
            "Unexpected character in a session value"
        }
        val plain = listOf(session.refreshToken, session.userId, session.email).joinToString(SEPARATOR)
        val encrypted = encrypt(plain) ?: return
        prefs.edit().putString(KEY_PAYLOAD, encrypted).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
    }

    // -----------------------------------------------------------------------

    private fun secretKey(): SecretKey? = try {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    } catch (e: Exception) {
        null
    }

    private fun encrypt(plain: String): String? {
        val key = secretKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            // GCM needs its nonce back to decrypt, and the nonce is not secret.
            // Length-prefixed so the format survives a future change of IV size.
            val iv = cipher.iv
            val packed = ByteArray(1 + iv.size + body.size)
            packed[0] = iv.size.toByte()
            iv.copyInto(packed, 1)
            body.copyInto(packed, 1 + iv.size)
            Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decrypt(encoded: String): String? {
        val key = secretKey() ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val body = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val PREFS_NAME = "fi_sabilillah_session"
        const val KEY_PAYLOAD = "session"
        const val KEY_ALIAS = "fi_sabilillah_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        /** ASCII unit separator: it cannot appear in a JWT, a UUID or an email address. */
        const val SEPARATOR = "\u001F"
    }
}
