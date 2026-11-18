package com.chinhsiang.premiumnotes.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 以 Android Keystore（AES-256-GCM）加密「已上鎖」筆記在本機資料庫中的內容。
 * 金鑰存在裝置的 Keystore 中、無法匯出，所以資料庫檔案被複製出去也無法讀取。
 * 注意：雲端同步時仍會以明文上傳（其他裝置沒有這把金鑰）。
 */
object NoteCrypto {
    private const val KEY_ALIAS = "gnote_locked_notes_key"
    private const val PREFIX = "gnote-enc1:"
    private const val TAG = "NoteCrypto"

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
        } catch (e: Throwable) {
            Log.e(TAG, "加密失敗，改以原文儲存", e)
            plain
        }
    }

    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value
        return try {
            val raw = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.e(TAG, "解密失敗", e)
            ""
        }
    }
}
