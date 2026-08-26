package com.shoujilunhui.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile
    private var retrofit: Retrofit? = null
    @Volatile
    private var cachedBase: String? = null

    fun api(baseUrl: String): Api {
        val base = if (baseUrl.endsWith("/")) baseUrl else baseUrl + "/"
        if (retrofit == null || cachedBase != base) {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            retrofit = Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            cachedBase = base
        }
        return retrofit!!.create(Api::class.java)
    }
}
