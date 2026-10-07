package com.aipos.madogit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.auth.CryptoManager
import com.aipos.madogit.data.auth.TokenManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        CryptoManager.testSecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        CryptoManager.testSecretKey = null
    }

    @Test
    fun `access token is served from memory without decrypting on every request`() {
        val tokenManager = TokenManager(context)
        tokenManager.saveAuthSuccess("  ghp_cached  ", FakeGitHubApiService().user)

        // Simulate the Keystore becoming unavailable: a per-request decrypt would now yield nothing.
        CryptoManager.testSecretKey = null

        assertEquals("ghp_cached", tokenManager.getAccessToken())
    }

    @Test
    fun `persisted session is restored by a new instance`() {
        TokenManager(context).saveAuthSuccess("ghp_restore", FakeGitHubApiService().user)

        val restored = TokenManager(context)

        assertEquals("ghp_restore", restored.getAccessToken())
        assertEquals("me", (restored.authState.value as AuthState.Authenticated).username)
    }

    @Test
    fun `clearing auth drops the cached token`() {
        val tokenManager = TokenManager(context)
        tokenManager.saveAuthSuccess("ghp_gone", FakeGitHubApiService().user)

        tokenManager.clearAuth()

        assertNull(tokenManager.getAccessToken())
        assertEquals(AuthState.Unauthenticated, tokenManager.authState.value)
    }
}
