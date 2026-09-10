package com.shoujilunhui.app.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    /**
     * 数字字段容错：服务端个别历史数据把整数字段存成了空字符串（如 chip_id=""），
     * 默认 Gson 会抛 NumberFormatException: empty String 导致整页加载失败。
     * 这里对数值类型做兜底：空串/空白/非数字字符串 → 可空类型返回 null、基本类型返回 0，合法数字字符串照常解析。
     */
    private class LenientNumberAdapterFactory : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            val fallback: Any? = when (raw) {
                Long::class.java, Integer::class.java,
                Double::class.java, Float::class.java,
                Short::class.java, Byte::class.java -> null // 可空（装箱）类型 → null
                Long::class.javaPrimitiveType -> 0L
                Int::class.javaPrimitiveType -> 0
                Double::class.javaPrimitiveType -> 0.0
                Float::class.javaPrimitiveType -> 0f
                Short::class.javaPrimitiveType -> 0.toShort()
                Byte::class.javaPrimitiveType -> 0.toByte()
                else -> return null
            }
            val delegate = gson.getDelegateAdapter(this, type)
            @Suppress("UNCHECKED_CAST")
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

                override fun read(reader: JsonReader): T {
                    if (reader.peek() == JsonToken.STRING) {
                        val s = reader.nextString().trim()
                        val parsed: Any? = when (raw) {
                            Long::class.java, Long::class.javaPrimitiveType -> s.toLongOrNull()
                            Integer::class.java, Int::class.javaPrimitiveType -> s.toIntOrNull()
                            Double::class.java, Double::class.javaPrimitiveType -> s.toDoubleOrNull()
                            Float::class.java, Float::class.javaPrimitiveType -> s.toFloatOrNull()
                            Short::class.java, Short::class.javaPrimitiveType -> s.toShortOrNull()
                            Byte::class.java, Byte::class.javaPrimitiveType -> s.toByteOrNull()
                            else -> null
                        }
                        return (parsed ?: fallback) as T
                    }
                    return delegate.read(reader)
                }
            }
        }
    }

    @Volatile
    private var retrofit: Retrofit? = null
    @Volatile
    private var cachedBase: String? = null

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(LenientNumberAdapterFactory())
        .create()

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
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            cachedBase = base
        }
        return retrofit!!.create(Api::class.java)
    }
}
