package zechs.drive.stream

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.data.local.ProfileEntity
import zechs.drive.stream.data.local.ProfileDataCleaner
import zechs.drive.stream.data.local.WatchListDatabase
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.model.WatchList

class ProfileMigrationTest {
    @Test
    fun pairingReplacesOnlyTheUntouchedStarterPairAndKeepsPrimaryHistory() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "profile-pairing-bootstrap-test"
        val context = object : android.content.ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefsName, mode)
        }
        val prefs = context.getSharedPreferences("ignored", 0)
        prefs.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(target, WatchListDatabase::class.java).build()
        val primary = UserProfile("profile-1", "Perfil principal", "avatar_anime", isDefault = true,
            libraryRootId = "drive-root", libraryRootName = "Animes", createdAt = 42,
            avatarUrl = "https://example.org/old-avatar.jpg",
            backgroundUrl = "https://example.org/old-background.jpg", isAdmin = true)
        val secondary = UserProfile("profile-2", "Perfil secundário", "avatar_anime", createdAt = 42)
        try {
            db.getProfileDao().replaceAll(listOf(ProfileEntity.from(primary), ProfileEntity.from(secondary)))
            db.getWatchListDao().upsertWatch(WatchList("Principal", "primary-video", 10, 100, profileId = primary.id))
            db.getWatchListDao().upsertWatch(WatchList("Secundário", "secondary-video", 10, 100, profileId = secondary.id))

            val manager = zechs.drive.stream.utils.ProfileManager(context,
                dagger.Lazy { ProfileDataCleaner(db) }, db)
            manager.prepareFirstProfileAfterPairing()

            assertTrue(manager.isInitialProfileSetupPending())
            assertEquals("", manager.getInitialProfileSetupTarget()?.name)
            assertEquals("profile_placeholder", manager.getInitialProfileSetupTarget()?.avatarResName)
            assertEquals("drive-root", manager.getInitialProfileSetupTarget()?.libraryRootId)
            assertNull(manager.getInitialProfileSetupTarget()?.avatarUrl)
            assertEquals(listOf(primary.id), manager.getProfiles().map { it.id })
            assertNotNull(db.getWatchListDao().getWatch("primary-video", primary.id))
            assertNull(db.getWatchListDao().getWatch("secondary-video", secondary.id))

            // Setup must survive Activity/process recreation between cleanup and the user's save.
            val restarted = zechs.drive.stream.utils.ProfileManager(context,
                dagger.Lazy { ProfileDataCleaner(db) }, db)
            restarted.awaitReady()
            assertTrue(restarted.isInitialProfileSetupPending())
            assertEquals("", restarted.getInitialProfileSetupTarget()?.name)
            assertEquals("drive-root", restarted.getInitialProfileSetupTarget()?.libraryRootId)

            restarted.saveProfile(restarted.getInitialProfileSetupTarget()!!.copy(name = "Meu perfil"))
            assertFalse(restarted.isInitialProfileSetupPending())
            assertEquals("Meu perfil", restarted.getProfiles().single().name)
            assertEquals("drive-root", restarted.getProfiles().single().libraryRootId)
            assertNotNull(db.getWatchListDao().getWatch("primary-video", primary.id))
        } finally {
            db.close()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun freshInstallPersistsOnlyTheUserCreatedProfile() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "profile-fresh-install-test"
        val context = object : android.content.ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                target.getSharedPreferences(prefsName, mode)
        }
        val prefs = context.getSharedPreferences("ignored", 0)
        prefs.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(target, WatchListDatabase::class.java).build()
        try {
            val manager = zechs.drive.stream.utils.ProfileManager(context,
                dagger.Lazy { zechs.drive.stream.data.local.ProfileDataCleaner(db) }, db)
            manager.awaitReady()
            assertTrue(manager.getProfiles().isEmpty())
            assertTrue(db.getProfileDao().getAll().isEmpty())
            manager.setLibraryRoot("prepared-root", "oneblacki")
            manager.saveProfile(UserProfile("my-profile", "Minha escolha", "profile_placeholder"))
            val saved = db.getProfileDao().getAll().single().toProfile()
            assertEquals("my-profile", saved.id)
            assertEquals("Minha escolha", saved.name)
            assertEquals("prepared-root", saved.libraryRootId)
            assertTrue(saved.isAdmin)
            assertNull(saved.avatarUrl)
        } finally {
            db.close()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun importsLegacyPreferencesOnceAndRoomWinsAfterRestart() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "profile-import-test"
        val context = object : android.content.ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefsName, mode)
        }
        val prefs = context.getSharedPreferences("ignored", 0)
        prefs.edit().clear().putString("profiles_list", """[
            {"id":"original-id","name":"Original","avatarResName":"avatar_anime",
             "isDefault":true,"libraryRootId":"drive-folder","createdAt":42}]
        """).putString("active_profile_id", "original-id").commit()
        val db = Room.inMemoryDatabaseBuilder(target, WatchListDatabase::class.java).build()
        fun manager() = zechs.drive.stream.utils.ProfileManager(context,
            dagger.Lazy { zechs.drive.stream.data.local.ProfileDataCleaner(db) }, db)
        try {
            val first = manager()
            first.awaitReady()
            val original = first.getActiveProfile()
            assertEquals("original-id", original.id)
            assertEquals("drive-folder", original.libraryRootId)
            val edited = original.copy(name = "Editado", avatarUrl = "https://example.org/portrait.jpg", isKids = true)
            first.saveProfile(edited)
            val restarted = manager()
            restarted.awaitReady()
            assertEquals(edited, restarted.getActiveProfile())
            assertEquals(
                zechs.drive.stream.utils.ProfileManagementPolicy.DeleteBlockReason.LAST_PROFILE,
                restarted.deleteProfile("original-id")
            )
        } finally {
            db.close()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun upgradeKeepsHistoryAndPersistsProfileArt() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "profile-migration-test.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("zechs.drive.stream.data.local.WatchListDatabase/6.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                }
            }
            db.execSQL("INSERT INTO watch_list(name,videoId,watchedDuration,totalDuration,profileId) VALUES('Episode','video',120,240,'legacy-id')")
            db.version = 6
        }
        fun open() = Room.databaseBuilder(context, WatchListDatabase::class.java, name)
            .addMigrations(
                WatchListDatabase.MIGRATION_6_7,
                WatchListDatabase.MIGRATION_7_8,
                WatchListDatabase.MIGRATION_8_9,
                WatchListDatabase.MIGRATION_9_10,
                WatchListDatabase.MIGRATION_10_11,
                WatchListDatabase.MIGRATION_11_12,
                WatchListDatabase.MIGRATION_12_13
            ).build()
        val profile = UserProfile("legacy-id", "Perfil", "avatar_anime", true, "drive-root", "Animes", 42,
            "https://example.org/avatar.jpg", "https://example.org/banner.jpg", true, true)
        try {
            val db = open()
            try {
                db.openHelper.writableDatabase.query("SELECT profileId,watchedDuration FROM watch_list").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("legacy-id", cursor.getString(0))
                    assertEquals(120L, cursor.getLong(1))
                }
                db.getProfileDao().replaceAll(listOf(ProfileEntity.from(profile)))
            } finally { db.close() }
            val reopened = open()
            try { assertEquals(profile, reopened.getProfileDao().getAll().single().toProfile()) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
