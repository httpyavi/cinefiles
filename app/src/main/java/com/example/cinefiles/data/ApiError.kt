package com.example.cinefiles.data

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

/** Thrown by [MovieRepository] when BuildConfig.TMDB_READ_TOKEN is empty, before any network call is made. */
class MissingTokenException : Exception("TMDB_READ_TOKEN is empty")

/**
 * What can go wrong with a TMDB call, each with a short title and a friendly message
 * (string resources, so a screen can show them with setText).
 */
enum class ApiError(@StringRes val title: Int, @StringRes val message: Int) {
    NO_INTERNET(R.string.error_no_internet_title, R.string.error_no_internet_message),
    TIMEOUT(R.string.error_timeout_title, R.string.error_timeout_message),
    TOKEN_MISSING(R.string.error_token_missing_title, R.string.error_token_missing_message),
    TOKEN_INVALID(R.string.error_token_invalid_title, R.string.error_token_invalid_message),
    SERVER(R.string.error_server_title, R.string.error_server_message),
    TOO_MANY_REQUESTS(R.string.error_too_many_title, R.string.error_too_many_message),
    UNKNOWN(R.string.error_unknown_title, R.string.error_unknown_message)
}

/** Turns whatever went wrong into one of the friendly [ApiError]s. Anything we do not recognise is UNKNOWN. */
fun friendlyError(error: Throwable): ApiError = when {
    error is MissingTokenException -> ApiError.TOKEN_MISSING

    // TMDB answered, but with an error code
    error is HttpException -> when (error.code()) {
        401, 403 -> ApiError.TOKEN_INVALID
        429 -> ApiError.TOO_MANY_REQUESTS
        in 500..599 -> ApiError.SERVER
        else -> ApiError.UNKNOWN
    }

    // No answer in time (this must be checked before IOException, because it is one)
    error is SocketTimeoutException -> ApiError.TIMEOUT

    // TMDB (or something in between) answered with text that is not JSON. Gson reports that as an IOException
    // too, so it must be checked before the next line, or it would be called "offline".
    error is MalformedJsonException -> ApiError.UNKNOWN

    // Could not reach TMDB at all: airplane mode, no signal, DNS trouble, a dropped connection
    error is IOException -> ApiError.NO_INTERNET

    // Everything else, including an answer we cannot read in other ways (a Gson parse error)
    else -> ApiError.UNKNOWN
}
