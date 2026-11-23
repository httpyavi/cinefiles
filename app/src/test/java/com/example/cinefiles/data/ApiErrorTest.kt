package com.example.cinefiles.data

import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Every kind of failure ends up as a friendly message, never as a crash. */
class ApiErrorTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun aMissingTokenIsReportedAsMissing() {
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(MissingTokenException()))
    }

    @Test
    fun noConnectionMeansNoInternet() {
        assertEquals(ApiError.NO_INTERNET, friendlyError(UnknownHostException("api.themoviedb.org")))
        assertEquals(ApiError.NO_INTERNET, friendlyError(ConnectException("failed to connect")))
        assertEquals(ApiError.NO_INTERNET, friendlyError(IOException("unexpected end of stream")))
    }

    @Test
    fun aSlowAnswerIsATimeoutNotJustAnIoError() {
        // SocketTimeoutException is an IOException too: it must still be recognised as a timeout
        assertEquals(ApiError.TIMEOUT, friendlyError(SocketTimeoutException("timeout")))
    }

    @Test
    fun aRejectedTokenIsReportedAsInvalid() {
        assertEquals(ApiError.TOKEN_INVALID, friendlyError(http(401)))
        assertEquals(ApiError.TOKEN_INVALID, friendlyError(http(403)))
    }

    @Test
    fun tooManyRequestsHasItsOwnMessage() {
        assertEquals(ApiError.TOO_MANY_REQUESTS, friendlyError(http(429)))
    }

    @Test
    fun serverTroubleIsAnyFiveHundred() {
        assertEquals(ApiError.SERVER, friendlyError(http(500)))
        assertEquals(ApiError.SERVER, friendlyError(http(503)))
    }

    @Test
    fun everythingElseIsUnknown() {
        assertEquals(ApiError.UNKNOWN, friendlyError(http(404)))
        assertEquals(ApiError.UNKNOWN, friendlyError(JsonSyntaxException("not json")))
        // Gson's MalformedJsonException is an IOException, but a garbled answer is not "offline"
        assertEquals(ApiError.UNKNOWN, friendlyError(MalformedJsonException("not json")))
        assertEquals(ApiError.UNKNOWN, friendlyError(IllegalStateException("surprise")))
    }
}
