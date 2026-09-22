package dev.ashwin.forcestop.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionStoreTest {
    @Test
    fun readFailureDoesNotEscapeToTheCollector() = runTest {
        val dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("Unreadable selection") }

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw AssertionError("A failed read must not overwrite storage")
        }

        val store = SelectionStore(dataStore)
        backgroundScope.launch { store.observe() }
        runCurrent()
        assertEquals(SelectionError.READ, store.state.value.error)
        assertFalse(store.state.value.ready)
    }

    @Test
    fun retryResubscribesWithoutOverwritingSavedSelection() = runTest {
        val disk = TestDataStore().apply { readFailure = IOException("Unavailable") }
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()
        store.toggle("other.app")
        assertEquals(0, disk.writes)

        disk.readFailure = null
        store.retryRead()
        runCurrent()
        assertEquals(2, disk.reads)
        assertEquals(setOf("saved.app"), store.state.value.packages)
        assertTrue(store.state.value.ready)
        assertEquals(0, disk.writes)
    }

    @Test
    fun failedWritesKeepConfirmedSelectionAndRequireReload() = runTest {
        val disk = TestDataStore()
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()
        disk.writeFailure = IOException("Disk full")

        store.toggle("other.app")
        assertEquals(SelectionError.SAVE, store.state.value.error)
        assertEquals(setOf("saved.app"), store.state.value.packages)
        assertFalse(store.state.value.ready)
        assertFalse(store.state.value.usable)
        store.clear()
        assertEquals(1, disk.writes)

        disk.writeFailure = null
        store.retryRead()
        runCurrent()
        assertTrue(store.state.value.ready)
        assertEquals(setOf("saved.app"), store.state.value.packages)
        store.toggle("other.app")
        assertEquals(setOf("saved.app", "other.app"), store.state.value.packages)
        store.clear()
        assertTrue(store.state.value.packages.isEmpty())
    }

    @Test
    fun failedReloadRetainsLastConfirmedDataIncludingOnCorruption() = runTest {
        val disk = TestDataStore()
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()
        disk.readFailure = CorruptionException("Invalid preferences")
        store.retryRead()
        assertFalse(store.state.value.ready)
        runCurrent()
        assertEquals(SelectionError.READ, store.state.value.error)
        assertEquals(setOf("saved.app"), store.state.value.packages)
        assertEquals(0, disk.writes)
    }

    @Test
    fun pendingWriteBlocksReadinessAndCancellationPropagates() = runTest {
        val disk = TestDataStore().apply { writeGate = CompletableDeferred() }
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()
        val write = launch { store.clear() }
        runCurrent()
        assertTrue(store.state.value.saving)
        assertFalse(store.state.value.ready)
        // A write in flight must not look like unreadable data to the UI.
        assertTrue(store.state.value.usable)
        store.retryRead()
        runCurrent()
        assertEquals(1, disk.reads)
        write.cancel()
        write.join()
        assertTrue(write.isCancelled)
        assertEquals(null, store.state.value.error)
        assertFalse(store.state.value.saving)
        assertEquals(setOf("saved.app"), store.state.value.packages)
    }

    @Test
    fun queuedTogglesAreSavedAfterThePendingWriteCompletes() = runTest {
        val disk = TestDataStore().apply { writeGate = CompletableDeferred() }
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()

        val firstWrite = launch { store.toggle("first.app") }
        runCurrent()
        val secondWrite = launch { store.toggle("second.app") }
        runCurrent()
        assertEquals(1, disk.writes)
        assertFalse(store.state.value.ready)
        assertTrue(store.state.value.usable)

        disk.writeGate?.complete(Unit)
        firstWrite.join()
        secondWrite.join()

        assertEquals(2, disk.writes)
        assertEquals(setOf("saved.app", "first.app", "second.app"), store.state.value.packages)
        assertTrue(store.state.value.ready)
    }

    @Test
    fun readCancellationIsNotReportedAsStorageFailure() = runTest {
        val disk = TestDataStore().apply { readFailure = CancellationException("Cancelled") }
        val store = SelectionStore(disk)
        backgroundScope.launch { store.observe() }
        runCurrent()
        assertEquals(1, disk.reads)
        assertEquals(null, store.state.value.error)
        assertFalse(store.state.value.ready)
    }

    private class TestDataStore : DataStore<Preferences> {
        private val preferences = MutableStateFlow(
            preferencesOf(stringSetPreferencesKey("selected_packages") to setOf("saved.app")),
        )
        var readFailure: Exception? = null
        var writeFailure: IOException? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var reads = 0
        var writes = 0

        override val data = flow {
            reads++
            readFailure?.let { throw it }
            emitAll(preferences)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writes++
            writeGate?.await()
            writeFailure?.let { throw it }
            return transform(preferences.value).also { preferences.value = it }
        }
    }
}