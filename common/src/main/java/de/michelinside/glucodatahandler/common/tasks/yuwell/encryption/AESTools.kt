package de.michelinside.glucodatahandler.common.tasks.yuwell.encryption

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.SecretKeySpec


object AESTools {
    private const val KEY_VALUE = "AES"

    fun generateKey(): ByteArray? {
        try {
            val keyGenerator = KeyGenerator.getInstance(KEY_VALUE)
            keyGenerator.init(256)
            return keyGenerator.generateKey().encoded
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        }
    }

    fun format(data: String?, key: ByteArray?): ByteArray? {
        if (data == null || key == null) {
            return null
        }
        return format(data.toByteArray(), key)
    }

    fun format(data: ByteArray?, key: ByteArray?): ByteArray? {
        if (data == null || key == null) {
            return null
        }
        try {
            val secretKeySpec = SecretKeySpec(key, KEY_VALUE)
            val cipher = Cipher.getInstance(KEY_VALUE)
            cipher.init(1, secretKeySpec)
            return cipher.doFinal(data)
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            return null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        } catch (e: BadPaddingException) {
            e.printStackTrace()
            return null
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
            return null
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
            return null
        }
    }

    fun parse(data: ByteArray?, key: ByteArray?): String? {
        if (data == null || key == null) {
            return null
        }
        try {
            val secretKeySpec = SecretKeySpec(key, KEY_VALUE)
            val cipher = Cipher.getInstance(KEY_VALUE)
            cipher.init(2, secretKeySpec)
            return String(cipher.doFinal(data))
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            return null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        } catch (e: BadPaddingException) {
            e.printStackTrace()
            return null
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
            return null
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
            return null
        }
    }
}