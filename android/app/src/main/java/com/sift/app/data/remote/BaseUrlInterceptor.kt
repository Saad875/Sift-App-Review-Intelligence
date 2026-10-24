package com.sift.app.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rewrites every request onto the backend URL chosen in Settings, so the base
 * URL can change at runtime without rebuilding Retrofit.
 */
class BaseUrlInterceptor(private val baseUrlProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = baseUrlProvider().toHttpUrlOrNull() ?: throw InvalidBaseUrlException()
        val target = base.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.trimStart('/'))
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(target).build())
    }
}

/** The stored backend URL cannot be parsed. An IOException so OkHttp reports it as a call failure. */
class InvalidBaseUrlException : IOException("Invalid backend URL")
