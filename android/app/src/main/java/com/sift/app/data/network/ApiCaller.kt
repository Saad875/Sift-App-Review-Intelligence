package com.sift.app.data.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sift.app.data.remote.dto.ErrorEnvelopeDto
import com.sift.app.domain.error.AppError
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Runs Retrofit calls and translates every failure into an [AppError]. */
class ApiCaller(
    private val connectivity: ConnectivityChecker,
    private val gson: Gson
) {
    suspend fun <T> call(block: suspend () -> T): T {
        if (!connectivity.isOnline()) throw AppError.NoInternet()
        return try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: AppError) {
            throw error
        } catch (http: HttpException) {
            throw mapHttp(http)
        } catch (io: IOException) {
            throw if (connectivity.isOnline()) AppError.BackendUnreachable(io) else AppError.NoInternet()
        } catch (parse: JsonParseException) {
            throw AppError.Unexpected(parse)
        }
    }

    private fun mapHttp(exception: HttpException): AppError {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val error = body?.let { runCatching { gson.fromJson(it, ErrorEnvelopeDto::class.java) }.getOrNull() }?.error
        return when (exception.code()) {
            HTTP_NOT_FOUND -> AppError.NotFound()
            HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited(error?.retryAfterSeconds)
            in HTTP_GATEWAY_ERRORS -> if (error?.message.isNullOrBlank()) {
                AppError.BackendUnreachable(exception)
            } else {
                AppError.Server(error?.code.orEmpty(), error?.message.orEmpty())
            }
            else -> if (error?.message.isNullOrBlank()) {
                AppError.Unexpected(exception)
            } else {
                AppError.Server(error?.code.orEmpty(), error?.message.orEmpty())
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_GATEWAY_ERRORS = 502..504
    }
}
