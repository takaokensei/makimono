package zechs.drive.stream.data.model

import androidx.annotation.Keep

@Keep
data class FileDetailsResponse(
    val id: String,
    val name: String,
    val parents: List<String>? = null
)
