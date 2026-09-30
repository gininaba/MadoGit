package com.aipos.madogit

import com.aipos.madogit.data.auth.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoManagerTest {

    @Before
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        CryptoManager.testSecretKey = keyGen.generateKey()
    }

    @After
    fun tearDown() {
        CryptoManager.testSecretKey = null
    }

    @Test
    fun `encrypt and decrypt roundtrip restores original token`() {
        val originalToken = "ghp_TestSecretToken1234567890abcdef"
        val encrypted = CryptoManager.encrypt(originalToken)

        assertNotEquals(originalToken, encrypted)
        assertTrue(encrypted.startsWith("enc:"))

        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(originalToken, decrypted)
    }

    @Test
    fun `encrypt returns empty string on blank input`() {
        assertEquals("", CryptoManager.encrypt(""))
        assertEquals("", CryptoManager.encrypt("   "))
    }

    @Test
    fun `decrypt handles unencrypted legacy strings gracefully`() {
        val legacyToken = "ghp_legacy_unencrypted_token"
        val decrypted = CryptoManager.decrypt(legacyToken)
        assertEquals(legacyToken, decrypted)
    }

    @Test
    fun `encrypt produces unique ciphertexts for each invocation due to randomized IV`() {
        val token = "ghp_secure_constant_token"
        val cipher1 = CryptoManager.encrypt(token)
        val cipher2 = CryptoManager.encrypt(token)

        assertNotEquals(cipher1, cipher2)
        assertEquals(CryptoManager.decrypt(cipher1), CryptoManager.decrypt(cipher2))
    }

    @Test
    fun `encrypt falls back to plaintext if keystore is unavailable`() {
        CryptoManager.testSecretKey = null
        val token = "ghp_fallback_test"
        val result = CryptoManager.encrypt(token)
        assertEquals(token, result)
    }
}
