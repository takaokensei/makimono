package zechs.drive.stream.data.model

import androidx.annotation.Keep

@Keep
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
