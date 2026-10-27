package zechs.drive.stream.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SeedboxTokenResponse(
    @Json(name = "daemon_token") val daemonToken: String
)

@JsonClass(generateAdapter = true)
data class SeedboxJobItem(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "anime_query") val animeQuery: String,
    @Json(name = "episodes_filter") val episodesFilter: String = "all",
    @Json(name = "prefer_ptbr") val preferPtbr: Int = 1,
    @Json(name = "status") val status: String,
    @Json(name = "progress_pct") val progressPct: Int = 0,
    @Json(name = "transfer_speed") val transferSpeed: String? = null,
    @Json(name = "drive_folder_id") val driveFolderId: String? = null,
    @Json(name = "status_detail") val statusDetail: String? = null,
    @Json(name = "created_at") val createdAt: Long = 0L,
    @Json(name = "updated_at") val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SeedboxStatusResponse(
    @Json(name = "is_online") val isOnline: Boolean,
    @Json(name = "last_heartbeat_at") val lastHeartbeatAt: Long? = null,
    @Json(name = "free_disk_gb") val freeDiskGb: Float? = null,
    @Json(name = "active_speed") val activeSpeed: String? = null,
    @Json(name = "jobs") val jobs: List<SeedboxJobItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SeedboxCreateJobRequest(
    @Json(name = "anime_query") val animeQuery: String,
    @Json(name = "episodes_filter") val episodesFilter: String = "all",
    @Json(name = "prefer_ptbr") val preferPtbr: Boolean = true,
    @Json(name = "magnet_uri") val magnetUri: String? = null
)

@JsonClass(generateAdapter = true)
data class SeedboxCreateJobResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class SeedboxJobsListResponse(
    @Json(name = "jobs") val jobs: List<SeedboxJobItem> = emptyList()
)
