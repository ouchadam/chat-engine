package app.dapk.st.engine

import app.dapk.engine.core.Base64
import app.dapk.engine.core.CoroutineDispatchers
import app.dapk.engine.core.JobBag
import app.dapk.engine.core.extensions.ErrorTracker
import app.dapk.st.domain.MatrixStoreModule
import app.dapk.st.matrix.MatrixClient
import app.dapk.st.matrix.MatrixTaskRunner
import app.dapk.st.matrix.auth.authService
import app.dapk.st.matrix.common.*
import app.dapk.st.matrix.crypto.MatrixMediaDecrypter
import app.dapk.st.matrix.crypto.cryptoService
import app.dapk.st.matrix.message.messageService
import app.dapk.st.matrix.push.pushService
import app.dapk.st.matrix.room.profileService
import app.dapk.st.matrix.room.roomService
import app.dapk.st.matrix.sync.syncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.InputStream
import java.time.Clock
import app.dapk.st.matrix.message.BackgroundScheduler as MatrixBackgroundScheduler
import app.dapk.st.matrix.message.internal.ImageContentReader as MatrixImageContentReader

class MatrixEngine internal constructor(
    private val directoryUseCase: Lazy<DirectoryUseCase>,
    private val matrix: Lazy<MatrixClient>,
    private val timelineUseCase: Lazy<ReadMarkingTimeline>,
    private val sendMessageUseCase: Lazy<SendMessageUseCase>,
    private val matrixMediaDecrypter: Lazy<MatrixMediaDecrypter>,
    private val matrixPushHandler: Lazy<MatrixPushHandler>,
    private val inviteUseCase: Lazy<InviteUseCase>,
    private val notificationMessagesUseCase: Lazy<ObserveUnreadNotificationsUseCase>,
    private val notificationInvitesUseCase: Lazy<ObserveInviteNotificationsUseCase>,
    private val credentialsStore: CredentialsStore,
) : ChatEngine {

    override fun directory() = directoryUseCase.value.state()
    override fun invites() = inviteUseCase.value.invites()

    override fun messages(roomId: RoomId, disableReadReceipts: Boolean): Flow<MessengerPageState> {
        return timelineUseCase.value.fetch(roomId, isReadReceiptsDisabled = disableReadReceipts)
    }

    override fun notificationsMessages(): Flow<UnreadNotifications> {
        return notificationMessagesUseCase.value.invoke()
    }

    override fun notificationsInvites(): Flow<InviteNotification> {
        return notificationInvitesUseCase.value.invoke()
    }

    override suspend fun login(request: LoginRequest): LoginResult {
        return matrix.value.authService().login(request.engine()).engine()
    }

    override suspend fun me(forceRefresh: Boolean): Me {
        return matrix.value.profileService().me(forceRefresh).engine()
    }

    override suspend fun InputStream.importRoomKeys(password: String): Flow<ImportResult> {
        return with(matrix.value.cryptoService()) {
            importRoomKeys(password).map { it.engine() }.onEach {
                when (it) {
                    is ImportResult.Error,
                    is ImportResult.Update -> {
                        // do nothing
                    }

                    is ImportResult.Success -> matrix.value.syncService().forceManualRefresh(it.roomIds)
                }
            }
        }
    }

    override suspend fun send(message: SendMessage, room: RoomOverview) {
        sendMessageUseCase.value.send(message, room)
    }

    override suspend fun registerPushToken(token: String, gatewayUrl: String) {
        matrix.value.pushService().registerPush(token, gatewayUrl)
    }

    override suspend fun joinRoom(roomId: RoomId) {
        matrix.value.roomService().joinRoom(roomId)
    }

    override suspend fun rejectRoom(roomId: RoomId) {
        matrix.value.roomService().rejectJoinRoom(roomId)
    }

    override suspend fun findMembersSummary(roomId: RoomId) = matrix.value.roomService().findMembersSummary(roomId)

    override fun mediaDecrypter(): MediaDecrypter {
        val mediaDecrypter = matrixMediaDecrypter.value
        return object : MediaDecrypter {
            override fun decrypt(input: InputStream, k: String, iv: String): MediaDecrypter.Collector {
                return MediaDecrypter.Collector {
                    mediaDecrypter.decrypt(input, k, iv).collect(it)
                }
            }
        }
    }

    override fun pushHandler() = matrixPushHandler.value

    override suspend fun muteRoom(roomId: RoomId) = matrix.value.roomService().muteRoom(roomId)

    override suspend fun unmuteRoom(roomId: RoomId) = matrix.value.roomService().unmuteRoom(roomId)

    override suspend fun isSignedIn() = credentialsStore.isSignedIn()
    override suspend fun preload() {
        credentialsStore.credentials()?.let {
            matrix.value.messageService().preloadEchos()
        }
    }

    override suspend fun kickUserFromRoom(roomId: RoomId, userId: UserId) {
        matrix.value.roomService().kick(roomId, userId, reason = "Kicked by admin")
    }

    override suspend fun runTask(task: ChatEngineTask): TaskRunner.TaskResult {
        return when (val result = matrix.value.run(MatrixTaskRunner.MatrixTask(task.type, task.jsonPayload))) {
            is MatrixTaskRunner.TaskResult.Failure -> TaskRunner.TaskResult.Failure(result.canRetry)
            MatrixTaskRunner.TaskResult.Success -> TaskRunner.TaskResult.Success
        }
    }


    class Factory {

        fun create(
            base64: Base64,
            logger: MatrixLogger,
            nameGenerator: DeviceDisplayNameGenerator,
            coroutineDispatchers: CoroutineDispatchers,
            errorTracker: ErrorTracker,
            imageContentReader: ImageContentReader,
            backgroundScheduler: BackgroundScheduler,
            storeModule: MatrixStoreModule,
            includeLogging: Boolean,
        ): ChatEngine {
            val credentialsStore = storeModule.credentialsStore()
            val roomStore = storeModule.roomStore()
            val overviewStore = storeModule.overviewStore()
            val lazyMatrix = lazy {
                MatrixFactory.createMatrix(
                    base64,
                    logger,
                    nameGenerator,
                    coroutineDispatchers,
                    errorTracker,
                    imageContentReader.matrix(),
                    backgroundScheduler,
                    storeModule.memberStore(),
                    roomStore,
                    storeModule.profileStore(),
                    storeModule.syncStore(),
                    overviewStore,
                    storeModule.filterStore(),
                    storeModule.localEchoStore,
                    credentialsStore,
                    storeModule.knownDevicesStore(),
                    storeModule.olmStore(base64),
                    includeLogging,
                )
            }
            val directoryUseCase = unsafeLazy {
                val matrix = lazyMatrix.value
                DirectoryUseCase(
                    matrix.syncService(),
                    matrix.messageService(),
                    credentialsStore,
                    roomStore,
                    DirectoryMergeWithLocalEchosUseCaseImpl(matrix.roomService()),
                )
            }
            val timelineUseCase = unsafeLazy {
                val matrix = lazyMatrix.value
                val mergeWithLocalEchosUseCase = TimelineMergeWithLocalEchosUseCaseImpl(LocalEchoMapper(MetaMapper()))
                val timeline = TimelineUseCaseImpl(matrix.syncService(), matrix.messageService(), matrix.roomService(), mergeWithLocalEchosUseCase)
                ReadMarkingTimeline(roomStore, credentialsStore, timeline, matrix.roomService())
            }

            val sendMessageUseCase = unsafeLazy {
                val matrix = lazyMatrix.value
                SendMessageUseCase(matrix.messageService(), LocalIdFactory(), imageContentReader.matrix(), Clock.systemUTC())
            }

            val mediaDecrypter = unsafeLazy { MatrixMediaDecrypter(base64) }
            val pushHandler = unsafeLazy {
                MatrixPushHandler(
                    backgroundScheduler.matrix(),
                    credentialsStore,
                    lazyMatrix.value.syncService(),
                    roomStore,
                    coroutineDispatchers,
                    JobBag(),
                )
            }

            val invitesUseCase = unsafeLazy { InviteUseCase(lazyMatrix.value.syncService()) }

            return MatrixEngine(
                directoryUseCase,
                lazyMatrix,
                timelineUseCase,
                sendMessageUseCase,
                mediaDecrypter,
                pushHandler,
                invitesUseCase,
                unsafeLazy { ObserveUnreadNotificationsUseCaseImpl(roomStore) },
                unsafeLazy { ObserveInviteNotificationsUseCaseImpl(overviewStore) },
                credentialsStore,
            )
        }

    }

}

private fun ImageContentReader.matrix(): MatrixImageContentReader {
    val engine = this
    return object : MatrixImageContentReader {
        override fun meta(uri: String) = engine.meta(uri).matrix()
        override fun inputStream(uri: String) = engine.inputStream(uri)
    }
}

private fun ImageContentReader.ImageContent.matrix(): MatrixImageContentReader.ImageContent {
    return MatrixImageContentReader.ImageContent(this.height, this.width, this.size, this.fileName, this.mimeType)
}

private fun <T> unsafeLazy(initializer: () -> T): Lazy<T> = lazy(mode = LazyThreadSafetyMode.NONE, initializer = initializer)

internal fun BackgroundScheduler.matrix() = MatrixBackgroundScheduler { key, task ->
    this.schedule(key, BackgroundScheduler.Task(task.type, task.jsonPayload))
}