package com.shoujilunhui.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class ModelRow(
    val id: Long,
    val brand: String,
    val category: String,
    val model: String,
    val price: String,
    val note: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class ModelsResponse(
    val total: Int,
    val page: Int,
    val limit: Int,
    val items: List<ModelRow>
)

data class BrandItem(val brand: String, val count: Int)
data class BrandsResponse(val items: List<BrandItem>, val total: Int)

data class PostBody(
    val brand: String,
    val category: String,
    val model: String,
    val price: String,
    val note: String
)

data class HealthResponse(val ok: Boolean, val models: Int, val time: String?)

interface Api {
    @GET("api/models")
    suspend fun getModels(
        @Query("brand") brand: String? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null
    ): ModelsResponse

    @GET("api/brands")
    suspend fun getBrands(): BrandsResponse

    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/models")
    suspend fun postModel(@Header("X-API-Key") key: String, @Body body: PostBody): ModelRow

    @PUT("api/models/{id}")
    suspend fun putModel(@Path("id") id: Long, @Header("X-API-Key") key: String, @Body body: Map<String, String>): ModelRow

    @DELETE("api/models/{id}")
    suspend fun deleteModel(@Path("id") id: Long, @Header("X-API-Key") key: String): Map<String, Any>
}
