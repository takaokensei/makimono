package zechs.drive.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import zechs.drive.stream.debug.DriveLibraryAuditDependencies
import zechs.drive.stream.utils.state.Resource

/** Manual integration diagnostic: requires a real paired account; never seeds or mutates data. */
@RunWith(AndroidJUnit4::class)
class RealDriveLibraryAuditTest {
    @Test fun inspectSelectedLibraryReadOnly() = runBlocking {
        withTimeout(90_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val deps = EntryPointAccessors.fromApplication(context, DriveLibraryAuditDependencies::class.java)
            val manager = deps.profileManager()
            manager.awaitReady()
            val (configuredRoot, name) = manager.getLibraryRoot()
            val root = configuredRoot ?: "root"
            println("LIBRARY_ROOT name=$name id=$root")
            val cached = deps.catalogRepository().getChildren(root).filter { it.name.contains("bleach", true) }
            cached.forEach { println("CACHED id=${it.id} name=${it.name} mime=${it.mimeType}") }
            val first = deps.driveRepository().getAllFilesDetailed("'$root' in parents and trashed = false", 100)
            assertTrue("Real Drive listing must succeed", first is Resource.Success)
            val folders = (first as Resource.Success).data.files
            println("ROOT_CONTENTS count=${folders.size}")
            val queue = java.util.ArrayDeque<Pair<String, Int>>()
            folders.filter { it.name.contains("bleach", true) }.forEach {
                println("ROOT_MATCH id=${it.id} name=${it.name} mime=${it.mimeType} target=${it.shortcutDetails.targetId}")
                queue.add((it.shortcutDetails.targetId ?: it.id) to 0)
            }
            val seen = mutableSetOf<String>()
            while (queue.isNotEmpty() && seen.size < 24) {
                val (id, depth) = queue.removeFirst()
                if (!seen.add(id)) continue
                val result = deps.driveRepository().getAllFilesDetailed("'$id' in parents and trashed = false", 100)
                if (result !is Resource.Success) {
                    println("CHILD_ERROR id=$id kind=${(result as? Resource.Error)?.errorKind}")
                    continue
                }
                val files = result.data.files
                println("CHILD id=$id depth=$depth count=${files.size} complete=${result.data.isComplete}")
                files.take(12).forEach { println("ENTRY id=${it.id} name=${it.name} mime=${it.mimeType} target=${it.shortcutDetails.targetId}") }
                if (depth < 4) files.filter { it.toDriveFile().isFolder || it.toDriveFile().isShortcutFolder }
                    .forEach { queue.add((it.shortcutDetails.targetId ?: it.id) to depth + 1) }
            }
        }
    }
}
