package zechs.drive.stream.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import zechs.drive.stream.data.model.UserProfile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val isAdmin: Boolean,
    val isKids: Boolean,
    val avatarResName: String,
    val isDefault: Boolean,
    val libraryRootId: String?,
    val libraryRootName: String?,
    val createdAt: Long
) {
    fun toProfile() = UserProfile(id, nome, avatarResName, isDefault, libraryRootId,
        libraryRootName, createdAt, avatarUrl, backgroundUrl, isAdmin, isKids)

    companion object {
        fun from(p: UserProfile) = ProfileEntity(p.id, p.name, p.avatarUrl, p.backgroundUrl,
            p.isAdmin, p.isKids, p.avatarResName, p.isDefault, p.libraryRootId,
            p.libraryRootName, p.createdAt)
    }
}
