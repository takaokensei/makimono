package zechs.drive.stream.ui.files.adapter

import androidx.annotation.Keep
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.utils.state.ResourceErrorKind

sealed class FilesDataModel {

    @Keep
    data class File(
        val driveFile: DriveFile
    ) : FilesDataModel()

    object Loading : FilesDataModel()

    data class PageError(val errorKind: ResourceErrorKind) : FilesDataModel()

}
