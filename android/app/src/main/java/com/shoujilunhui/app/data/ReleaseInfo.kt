package com.shoujilunhui.app.data

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String?,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    val assets: List<ReleaseAsset>? = emptyList()
)

data class ReleaseAsset(
    val name: String?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String?,
    val size: Long? = null
)
