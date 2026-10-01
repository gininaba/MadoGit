package com.aipos.madogit.data.api

import com.aipos.madogit.data.auth.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val GITHUB_BASE_URL = "https://api.github.com/"
    @Volatile
    private var httpCache: Cache? = null

    private val sharedMoshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val baseHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.aipos.madogit.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Synchronized
    fun initCache(cacheDir: File) {
        if (httpCache == null) {
            val cacheSize = 15L * 1024 * 1024 // 15 MB HTTP response cache for GitHub ETag / 304 support
            httpCache = Cache(File(cacheDir, "http_github_cache"), cacheSize)
        }
    }

    fun createRetrofit(
        tokenManager: TokenManager,
        onRateLimitUpdated: ((Int, Int) -> Unit)? = null
    ): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GitHub-Assistant-Notifier-Android")

            // Attach Bearer token if available
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val rateLimitInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val limitStr = response.header("x-ratelimit-limit")
            val remainingStr = response.header("x-ratelimit-remaining")
            if (limitStr != null && remainingStr != null) {
                try {
                    val limit = limitStr.toInt()
                    val remaining = remainingStr.toInt()
                    onRateLimitUpdated?.invoke(remaining, limit)
                } catch (_: Exception) {
                    // ignore
                }
            }
            response
        }

        val builder = baseHttpClient.newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(rateLimitInterceptor)
        httpCache?.let { builder.cache(it) }
        val okHttpClient = builder.build()

        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(sharedMoshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    fun createRetrofitWithToken(token: String): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GitHub-Assistant-Notifier-Android")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${token.trim()}")
            }

            chain.proceed(requestBuilder.build())
        }

        val builder = baseHttpClient.newBuilder()
            .addInterceptor(authInterceptor)
        httpCache?.let { builder.cache(it) }
        val okHttpClient = builder.build()

        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(sharedMoshi))
            .build()
            .create(GitHubApiService::class.java)
    }
}
