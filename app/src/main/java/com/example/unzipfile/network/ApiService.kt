package com.example.unzipfile.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {

    @GET("api/users/profile")
    suspend fun getProfile(): ApiResponse<com.example.unzipfile.membership.UserProfile>

    companion object {
        private const val BASE_URL = "http://192.168.1.159:8080/" // Update to your core-api URL
        private const val APP_KEY = "UnzipFile_App_v2_2026"

        fun create(): ApiService {
            val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            
            val appKeyInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-App-Key", APP_KEY)
                    .build()
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .addInterceptor(appKeyInterceptor)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)
