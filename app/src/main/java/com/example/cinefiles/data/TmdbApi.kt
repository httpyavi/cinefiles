package com.example.cinefiles.data

import com.example.cinefiles.BuildConfig
import com.example.cinefiles.data.dto.MovieDto
import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.logic.ContentFilter
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The TMDB calls we use. Retrofit turns each function into a web request.
 *
 * Every list call asks for English text (`language=en-US`) and no adult movies (`include_adult=false`);
 * that is what the default values are for, so callers never have to think about them.
 * Only [MovieRepository] should call these functions: it also handles the errors.
 */
interface TmdbApi {

    @GET("trending/movie/week")
    suspend fun trending(
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE
    ): MovieListResponse

    @GET("movie/popular")
    suspend fun popular(
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE
    ): MovieListResponse

    // Used by the lists in a later batch (the deck's "top rated" kind goes through /discover instead)
    @GET("movie/top_rated")
    suspend fun topRated(
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE
    ): MovieListResponse

    @GET("discover/movie")
    suspend fun discover(
        @Query("with_genres") withGenres: String? = null,                    // "35|28" means comedy OR action
        @Query("primary_release_date.gte") releasedFrom: String? = null,     // "2015-01-01"
        @Query("primary_release_date.lte") releasedTo: String? = null,
        @Query("vote_average.gte") minRating: Double? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("vote_count.lte") maxVotes: Int? = null,
        @Query("with_runtime.gte") minRuntime: Int? = null,
        @Query("with_runtime.lte") maxRuntime: Int? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("without_companies") withoutCompanies: String? = EXCLUDED_COMPANIES,
        @Query("without_keywords") withoutKeywords: String? = EXCLUDED_KEYWORDS,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE,
        @Query("include_adult") includeAdult: Boolean = false
    ): MovieListResponse

    /** One movie with everything for the Details screen: credits (cast, crew) and videos (trailers) come with it. */
    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos",
        @Query("language") language: String = LANGUAGE
    ): MovieDto

    @GET("movie/{id}/similar")
    suspend fun similar(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE
    ): MovieListResponse

    @GET("search/movie")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE,
        @Query("include_adult") includeAdult: Boolean = false
    ): MovieListResponse

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val LANGUAGE = "en-US"
        const val EXCLUDED_COMPANIES = ContentFilter.EXCLUDED_COMPANIES
        const val EXCLUDED_KEYWORDS = ContentFilter.EXCLUDED_KEYWORDS
        private const val TIMEOUT_SECONDS = 15L

        /**
         * Builds the API. Called ONCE, by CineFilesApp.
         * [readToken] is TMDB's "API Read Access Token". It is sent as `Authorization: Bearer ...` with every call.
         * [baseUrl] is only changed by the tests, which talk to a pretend TMDB on this computer.
         */
        fun create(readToken: String, baseUrl: String = BASE_URL): TmdbApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $readToken")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .apply {
                    // Debug builds only: one Logcat line per call (address and answer code, no headers, no body).
                    // The Authorization header is also blacked out, so the token can never be printed.
                    if (BuildConfig.DEBUG) {
                        val logging = HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        }
                        addInterceptor(logging)
                    }
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbApi::class.java)
        }
    }
}
