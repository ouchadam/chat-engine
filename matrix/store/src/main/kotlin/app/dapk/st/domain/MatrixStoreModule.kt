package app.dapk.st.domain

import app.dapk.db.DapkDb
import app.dapk.engine.core.Base64
import app.dapk.engine.core.CoroutineDispatchers
import app.dapk.engine.core.Preferences
import app.dapk.engine.core.extensions.ErrorTracker
import app.dapk.engine.core.extensions.unsafeLazy
import app.dapk.st.domain.localecho.LocalEchoPersistence
import app.dapk.st.domain.profile.ProfilePersistence
import app.dapk.st.domain.room.MutedStorePersistence
import app.dapk.st.domain.sync.OverviewPersistence
import app.dapk.st.domain.sync.RoomPersistence
import app.dapk.st.matrix.common.CredentialsStore
import app.dapk.st.matrix.message.LocalEchoStore
import app.dapk.st.matrix.room.MemberStore
import app.dapk.st.matrix.room.ProfileStore
import app.dapk.st.matrix.sync.FilterStore
import app.dapk.st.matrix.sync.OverviewStore
import app.dapk.st.matrix.sync.RoomStore
import app.dapk.st.matrix.sync.SyncStore
import app.dapk.st.olm.OlmPersistenceWrapper

class MatrixStoreModule(
    private val database: DapkDb,
    val preferences: Preferences,
    private val credentialPreferences: Preferences,
    private val errorTracker: ErrorTracker,
    private val coroutineDispatchers: CoroutineDispatchers,
) {

    private val muteableStore by unsafeLazy { MutedStorePersistence(database, coroutineDispatchers) }

    fun overviewStore(): OverviewStore = OverviewPersistence(database, coroutineDispatchers)
    fun roomStore(): RoomStore {
        return RoomPersistence(
            database = database,
            overviewPersistence = OverviewPersistence(database, coroutineDispatchers),
            coroutineDispatchers = coroutineDispatchers,
            muteableStore = muteableStore,
        )
    }

    fun profileStore(): ProfileStore = ProfilePersistence(preferences)

    fun credentialsStore(): CredentialsStore = CredentialsPreferences(credentialPreferences)
    fun syncStore(): SyncStore = SyncTokenPreferences(preferences)
    fun filterStore(): FilterStore = FilterPreferences(preferences)
    val localEchoStore: LocalEchoStore by unsafeLazy { LocalEchoPersistence(errorTracker, database) }
    fun olmStore(base64: Base64) = OlmPersistenceWrapper(OlmPersistenceStore(database, credentialsStore(), coroutineDispatchers), base64)
    fun knownDevicesStore() = DevicePersistence(database, KnownDevicesCache(), coroutineDispatchers)

    fun memberStore(): MemberStore {
        return MemberPersistence(database, coroutineDispatchers)
    }
}
