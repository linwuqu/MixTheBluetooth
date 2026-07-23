package com.biosensor.migratedev.port.adapter.localport

import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionCipherTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val aad = "auth_session_secure/session_blob_v1".toByteArray()

    @Test
    fun encryptedPayloadCanBeDecrypted() {
        val plaintext = "token-value".toByteArray()

        val encrypted = SessionCipher.encrypt(plaintext, key, aad)

        assertArrayEquals(plaintext, SessionCipher.decrypt(encrypted, key, aad))
    }

    @Test
    fun eachEncryptionUsesADifferentIv() {
        val plaintext = "same-value".toByteArray()

        val first = SessionCipher.encrypt(plaintext, key, aad)
        val second = SessionCipher.encrypt(plaintext, key, aad)

        assertFalse(first.contentEquals(second))
    }

    @Test(expected = java.security.GeneralSecurityException::class)
    fun tamperingFailsAuthentication() {
        val encrypted = SessionCipher.encrypt("token-value".toByteArray(), key, aad)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        SessionCipher.decrypt(encrypted, key, aad)
    }
}
