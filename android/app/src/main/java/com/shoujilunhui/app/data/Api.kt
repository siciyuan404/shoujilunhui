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

data class VariantItem(
    val spec: String,
    val price: String
)

data class ModelRow(
    val id: Long,
    val brand: String,
    val category: String,
    val model: String,
    val price: String,
    val note: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("cpu_brand") val cpuBrand: String?,
    @SerializedName("cpu_model") val cpuModel: String?,
    val ram: String?,
    val rom: String?,
    @SerializedName("back_camera") val backCamera: String?,
    @SerializedName("front_camera") val frontCamera: String?,
    @SerializedName("screen_size") val screenSize: String?,
    @SerializedName("screen_type") val screenType: String?,
    val refresh: String?,
    val battery: String?,
    val charge: String?,
    val network: String?,
    val os: String?,
    val variants: List<VariantItem>?,
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

data class FiltersResponse(
    val years: List<String>?,
    @SerializedName("cpu_brands") val cpuBrands: List<String>?,
    @SerializedName("screen_types") val screenTypes: List<String>?
)

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
        @Query("sort") sort: String? = null,
        @Query("year") year: String? = null,
        @Query("cpu_brand") cpuBrand: String? = null,
        @Query("camera_min") cameraMin: Int? = null
    ): ModelsResponse

    @GET("api/brands")
    suspend fun getBrands(): BrandsResponse

    @GET("api/filters")
    suspend fun getFilters(): FiltersResponse

    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/models")
    suspend fun postModel(@Header("X-API-Key") key: String, @Body body: PostBody): ModelRow

    @PUT("api/models/{id}")
    suspend fun putModel(@Path("id") id: Long, @Header("X-API-Key") key: String, @Body body: Map<String, String>): ModelRow

    @DELETE("api/models/{id}")
    suspend fun deleteModel(@Path("id") id: Long, @Header("X-API-Key") key: String): Map<String, Any>
}
