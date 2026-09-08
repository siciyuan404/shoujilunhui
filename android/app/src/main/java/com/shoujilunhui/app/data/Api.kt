package com.shoujilunhui.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
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
    @SerializedName("model_code") val modelCode: String?,
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
    val images: List<String>?,
    val variants: List<VariantItem>?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("vendor") val vendor: String? = null,
    @SerializedName("vendor_pnp") val vendorPnp: String? = null,
    @SerializedName("package") val packageName: String? = null,
    @SerializedName("rom_type") val romType: String? = null,
    @SerializedName("rom_size") val romSize: String? = null,
    @SerializedName("chip_id") val chipId: Long? = null
)

// ---------- 存储芯片（storage_chips 库） ----------

data class ChipRow(
    val id: Long,
    @SerializedName("chip_class") val chipClass: String? = null,
    val vendor: String? = null,
    @SerializedName("vendor_pnp") val vendorPnp: String? = null,
    @SerializedName("package") val packageName: String? = null,
    @SerializedName("rom_type") val romType: String? = null,
    @SerializedName("rom_size") val romSize: String? = null,
    @SerializedName("ram_type") val ramType: String? = null,
    @SerializedName("ram_size") val ramSize: String? = null,
    @SerializedName("ref_count") val refCount: Int = 0
)

data class ChipsResponse(
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 0,
    val items: List<ChipRow> = emptyList()
)

data class ChipMeta(
    val classes: List<String>? = null,
    val vendors: List<String>? = null,
    val packages: List<String>? = null,
    @SerializedName("rom_types") val romTypes: List<String>? = null,
    @SerializedName("rom_sizes") val romSizes: List<String>? = null,
    @SerializedName("ram_sizes") val ramSizes: List<String>? = null
)

data class ChipRefModel(
    val id: Long,
    val brand: String,
    val category: String,
    val model: String,
    val price: String,
    val rom: String? = null,
    val ram: String? = null
)

data class ChipDetail(
    val id: Long,
    @SerializedName("chip_class") val chipClass: String? = null,
    val vendor: String? = null,
    @SerializedName("vendor_pnp") val vendorPnp: String? = null,
    @SerializedName("package") val packageName: String? = null,
    @SerializedName("rom_type") val romType: String? = null,
    @SerializedName("rom_size") val romSize: String? = null,
    @SerializedName("ram_type") val ramType: String? = null,
    @SerializedName("ram_size") val ramSize: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val models: List<ChipRefModel> = emptyList()
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
    val note: String,
    val images: List<String>? = null
)

data class HealthResponse(val ok: Boolean, val models: Int, val time: String?)

// ---------- 收机记账 ----------

data class RecordRow(
    val id: Long,
    val photo: String,
    val brand: String,
    val category: String,
    val model: String,
    @SerializedName("model_id") val modelId: Long?,
    @SerializedName("rec_price") val recPrice: String,
    @SerializedName("sale_price") val salePrice: String,
    val channel: String,
    @SerializedName("sale_channel") val saleChannel: String,
    val seller: String,
    val day: String,
    val status: String,
    val note: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class RecordsResponse(
    val total: Int,
    val page: Int,
    val limit: Int,
    val items: List<RecordRow>
)

data class StatsRange(val start: String, val end: String)

data class Summary(
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double,
    val channels: Int,
    val statuses: Int,
    val sellers: Int = 0
)

data class DayStat(
    val day: String,
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double
)

data class ChannelStat(
    val channel: String,
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double
)

data class ModelStat(
    val model: String,
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double
)

data class StatusStat(
    val status: String,
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double
)

data class SellerStat(
    val seller: String,
    val count: Int,
    @SerializedName("recTotal") val recTotal: Double,
    @SerializedName("saleTotal") val saleTotal: Double,
    val profit: Double
)

data class StatsResponse(
    val range: StatsRange?,
    val summary: Summary?,
    @SerializedName("byDay") val byDay: List<DayStat>?,
    @SerializedName("byChannel") val byChannel: List<ChannelStat>?,
    @SerializedName("byModel") val byModel: List<ModelStat>?,
    @SerializedName("byStatus") val byStatus: List<StatusStat>?,
    @SerializedName("bySeller") val bySeller: List<SellerStat>?
)

data class RecordPostBody(
    val photo: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val model: String,
    @SerializedName("model_id") val modelId: Long? = null,
    @SerializedName("rec_price") val recPrice: String? = null,
    @SerializedName("sale_price") val salePrice: String? = null,
    val channel: String? = null,
    @SerializedName("sale_channel") val saleChannel: String? = null,
    val seller: String? = null,
    val day: String? = null,
    val status: String? = null,
    val note: String? = null
)

data class UploadResponse(val ok: Boolean, val url: String?, val name: String?)

data class RecordBatchBody(val items: List<RecordPostBody>)

data class RecordPatchBody(
    val model: String? = null,
    val brand: String? = null,
    val category: String? = null,
    @SerializedName("model_id") val modelId: Long? = null,
    @SerializedName("rec_price") val recPrice: String? = null,
    @SerializedName("sale_price") val salePrice: String? = null,
    val channel: String? = null,
    @SerializedName("sale_channel") val saleChannel: String? = null,
    val seller: String? = null,
    val day: String? = null,
    val status: String? = null,
    val note: String? = null,
    val photo: String? = null
)

data class ModelPatchBody(
    val price: String? = null,
    val note: String? = null,
    val images: List<String>? = null,
    @SerializedName("model_code") val modelCode: String? = null
)

data class BatchResult(val ok: Boolean, val inserted: Int)

data class IdResult(val ok: Boolean, val id: Long)

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

    // ---------- 存储芯片 ----------

    @GET("api/chips/meta")
    suspend fun getChipMeta(): ChipMeta

    @GET("api/chips")
    suspend fun getChips(
        @Query("search") search: String? = null,
        @Query("chip_class") chipClass: String? = null,
        @Query("vendor") vendor: String? = null,
        @Query("rom_size") romSize: String? = null,
        @Query("ram_size") ramSize: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int? = null
    ): ChipsResponse

    @GET("api/chips/{id}")
    suspend fun getChipDetail(@Path("id") id: Long): ChipDetail

    @POST("api/models")
    suspend fun postModel(@Header("X-API-Key") key: String, @Body body: PostBody): ModelRow

    @PUT("api/models/{id}")
    suspend fun putModel(@Path("id") id: Long, @Header("X-API-Key") key: String, @Body body: ModelPatchBody): ModelRow

    @DELETE("api/models/{id}")
    suspend fun deleteModel(@Path("id") id: Long, @Header("X-API-Key") key: String): IdResult

    // ---------- 收机记账 ----------

    @GET("api/records")
    suspend fun getRecords(
        @Query("period") period: String? = null,
        @Query("day") day: String? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int? = 500
    ): RecordsResponse

    @GET("api/records/stats")
    suspend fun getRecordStats(
        @Query("period") period: String? = null,
        @Query("day") day: String? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null
    ): StatsResponse

    @POST("api/records")
    suspend fun postRecord(@Header("X-API-Key") key: String, @Body body: RecordPostBody): RecordRow

    @POST("api/records/batch")
    suspend fun postRecordsBatch(@Header("X-API-Key") key: String, @Body body: RecordBatchBody): BatchResult

    @PUT("api/records/{id}")
    suspend fun putRecord(@Path("id") id: Long, @Header("X-API-Key") key: String, @Body body: RecordPatchBody): RecordRow

    @DELETE("api/records/{id}")
    suspend fun deleteRecord(@Path("id") id: Long, @Header("X-API-Key") key: String): IdResult

    @POST("api/upload")
    suspend fun uploadImage(@Header("X-API-Key") key: String, @Body body: RequestBody): UploadResponse
}
