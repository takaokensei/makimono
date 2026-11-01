package zechs.drive.stream.domain.library

internal object LibraryRootChangePolicy {
    fun needsRefresh(cachedProfile: String?, cachedRoot: String?, profile: String, root: String?): Boolean =
        cachedRoot != null && (cachedProfile != profile || cachedRoot != (root ?: "root"))
}
