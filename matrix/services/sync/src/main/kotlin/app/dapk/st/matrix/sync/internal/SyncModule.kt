package app.dapk.st.matrix.sync.internal

import app.dapk.engine.core.CoroutineDispatchers
import app.dapk.engine.core.extensions.ErrorTracker
import app.dapk.engine.core.extensions.unsafeLazy
import app.dapk.st.matrix.common.CredentialsStore
import app.dapk.st.matrix.common.MatrixLogger
import app.dapk.st.matrix.http.MatrixHttpClient
import app.dapk.st.matrix.sync.*
import app.dapk.st.matrix.sync.internal.filter.FilterUseCase
import app.dapk.st.matrix.sync.internal.overview.ReducedSyncFilterUseCase
import app.dapk.st.matrix.sync.internal.room.MessageDecrypter
import app.dapk.st.matrix.sync.internal.room.RoomEventsDecrypter
import app.dapk.st.matrix.sync.internal.room.SyncEventDecrypter
import app.dapk.st.matrix.sync.internal.room.SyncSideEffects
import app.dapk.st.matrix.sync.internal.sync.*
import app.dapk.st.matrix.sync.internal.sync.message.RichMessageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

internal class SyncModule(
    httpClient: MatrixHttpClient,
    syncStore: SyncStore,
    overviewStore: OverviewStore,
    roomStore: RoomStore,
    filterStore: FilterStore,
    messageDecrypter: MessageDecrypter,
    keySharer: KeySharer,
    verificationHandler: VerificationHandler,
    deviceNotifier: DeviceNotifier,
    json: Json,
    oneTimeKeyProducer: MaybeCreateMoreKeys,
    credentialsStore: CredentialsStore,
    roomMembersService: RoomMembersService,
    logger: MatrixLogger,
    errorTracker: ErrorTracker,
    coroutineDispatchers: CoroutineDispatchers,
    syncConfig: SyncConfig,
    richMessageParser: RichMessageParser,
) {

    val syncEventsFlow = MutableStateFlow<List<SyncService.SyncEvent>>(emptyList())

    private val roomDataSource by unsafeLazy { RoomDataSource(roomStore, logger) }
    private val eventDecrypter by unsafeLazy { SyncEventDecrypter(messageDecrypter, json, logger) }
    private val roomEventsDecrypter by unsafeLazy { RoomEventsDecrypter(messageDecrypter, richMessageParser, json, logger) }

    val roomRefresher by lazy { RoomRefresher(roomDataSource, roomEventsDecrypter, logger) }

    val syncUseCase by unsafeLazy {
        val roomDataSource = RoomDataSource(roomStore, logger)
        val syncReducer = SyncReducer(
            RoomProcessor(
                roomMembersService,
                roomDataSource,
                TimelineEventsProcessor(
                    RoomEventCreator(roomMembersService, errorTracker, RoomEventFactory(roomMembersService, richMessageParser), richMessageParser),
                    roomEventsDecrypter,
                    eventDecrypter,
                    EventLookupUseCase(roomStore)
                ),
                RoomOverviewProcessor(roomMembersService),
                UnreadEventsProcessor(roomStore, logger),
                EphemeralEventsUseCase(roomMembersService, syncEventsFlow),
            ),
            roomRefresher,
            roomDataSource,
            logger,
            errorTracker,
            coroutineDispatchers,
        )
        SyncUseCase(
            overviewStore,
            SideEffectFlowIterator(logger, errorTracker),
            SyncSideEffects(keySharer, verificationHandler, deviceNotifier, messageDecrypter, json, oneTimeKeyProducer, logger, syncConfig),
            httpClient,
            syncStore,
            syncReducer,
            credentialsStore,
            logger,
            ReducedSyncFilterUseCase(FilterUseCase(httpClient, filterStore)),
            syncConfig,
        )
    }

}