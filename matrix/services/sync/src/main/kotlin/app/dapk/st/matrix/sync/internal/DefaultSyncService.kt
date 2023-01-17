package app.dapk.st.matrix.sync.internal

import app.dapk.engine.core.CoroutineDispatchers
import app.dapk.engine.core.withIoContext
import app.dapk.st.matrix.common.CredentialsStore
import app.dapk.st.matrix.common.EventId
import app.dapk.st.matrix.common.RoomId
import app.dapk.st.matrix.sync.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*

internal class DefaultSyncService(
    private val syncStore: SyncStore,
    private val overviewStore: OverviewStore,
    private val roomStore: RoomStore,
    scope: CoroutineScope,
    private val credentialsStore: CredentialsStore,
    private val coroutineDispatchers: CoroutineDispatchers,
    syncConfig: SyncConfig,
    private val syncModule: SyncModule,
) : SyncService {

    private val syncFlow by lazy { syncModule.syncUseCase.sync().share(syncConfig, scope) }

    private fun Flow<Unit>.share(config: SyncConfig, scope: CoroutineScope): Flow<Unit> {
        return if (config.allowSharedFlows) this.shareIn(scope, SharingStarted.WhileSubscribed(5000)) else this
    }

    override fun startSyncing(): Flow<Unit> {
        return flow { emit(syncStore.read(SyncStore.SyncKey.Overview) != null) }.flatMapConcat { hasSynced ->
            when (hasSynced) {
                true -> syncFlow.filter { false }.onStart { emit(Unit) }
                false -> {
                    var counter = 0
                    syncFlow.filter { counter < 1 }.onEach { counter++ }
                }
            }
        }
    }

    override fun invites() = overviewStore.latestInvites()
    override fun overview() = overviewStore.latest()
    override fun room(roomId: RoomId) = roomStore.latest(roomId)
    override fun events(roomId: RoomId?) = roomId?.let { syncModule.syncEventsFlow.byRoomId(roomId) } ?: syncModule.syncEventsFlow

    private fun MutableStateFlow<List<SyncService.SyncEvent>>.byRoomId(roomId: RoomId) = this.map { it.filter { it.roomId == roomId } }.distinctUntilChanged()

    override suspend fun observeEvent(eventId: EventId) = roomStore.observeEvent(eventId)
    override suspend fun forceManualRefresh(roomIds: Set<RoomId>) {
        coroutineDispatchers.withIoContext {
            roomIds.map {
                async {
                    syncModule.roomRefresher.refreshRoomContent(it, credentialsStore.credentials()!!)?.also {
                        overviewStore.persist(listOf(it.roomOverview))
                    }
                }
            }.awaitAll()
        }
    }
}
