package app.dapk.st.domain

import app.dapk.db.DapkDb
import app.dapk.db.model.DbCryptoAccount
import app.dapk.db.model.DbCryptoMegolmInbound
import app.dapk.db.model.DbCryptoMegolmOutbound
import app.dapk.engine.core.CoroutineDispatchers
import app.dapk.engine.core.withIoContext
import app.dapk.st.matrix.common.CredentialsStore
import app.dapk.st.matrix.common.Curve25519
import app.dapk.st.matrix.common.RoomId
import app.dapk.st.matrix.common.SessionId
import app.dapk.st.olm.OlmPersistence
import app.dapk.st.olm.SerializedObject
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class OlmPersistenceStore(
    private val database: DapkDb,
    private val credentialsStore: CredentialsStore,
    private val dispatchers: CoroutineDispatchers,
) : OlmPersistence {

    override suspend fun read(): String? {
        return dispatchers.withIoContext {
            database.cryptoQueries
                .selectAccount(credentialsStore.credentials()!!.userId.value)
                .executeAsOneOrNull()
        }
    }

    override suspend fun persist(olmAccount: SerializedObject) {
        dispatchers.withIoContext {
            database.cryptoQueries.insertAccount(
                DbCryptoAccount(
                    user_id = credentialsStore.credentials()!!.userId.value,
                    blob = olmAccount.value
                )
            )
        }
    }

    override suspend fun readOutbound(roomId: RoomId): Pair<Long, String>? {
        return dispatchers.withIoContext {
            database.cryptoQueries
                .selectMegolmOutbound(roomId.value)
                .executeAsOneOrNull()?.let {
                    it.utcEpochMillis to it.blob
                }
        }
    }

    override suspend fun persistOutbound(roomId: RoomId, creationTimestampUtc: Long, outboundGroupSession: SerializedObject) {
        dispatchers.withIoContext {
            database.cryptoQueries.insertMegolmOutbound(
                DbCryptoMegolmOutbound(
                    room_id = roomId.value,
                    blob = outboundGroupSession.value,
                    utcEpochMillis = creationTimestampUtc,
                )
            )
        }
    }

    override suspend fun persistSession(identity: Curve25519, sessionId: SessionId, olmSession: SerializedObject) {
        withContext(dispatchers.io) {
            database.cryptoQueries.insertOlmSession(
                identity_key = identity.value,
                session_id = sessionId.value,
                blob = olmSession.value,
            )
        }
    }

    override suspend fun readSessions(identities: List<Curve25519>): List<Pair<Curve25519, String>>? {
        return withContext(dispatchers.io) {
            database.cryptoQueries
                .selectOlmSession(identities.map { it.value })
                .executeAsList()
                .map { Curve25519(it.identity_key) to it.blob }
                .takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun persist(sessionId: SessionId, inboundGroupSession: SerializedObject) {
        withContext(dispatchers.io) {
            database.cryptoQueries.insertMegolmInbound(
                DbCryptoMegolmInbound(
                    session_id = sessionId.value,
                    blob = inboundGroupSession.value
                )
            )
        }
    }

    override suspend fun readInbound(sessionId: SessionId): SerializedObject? {
        return withContext(dispatchers.io) {
            database.cryptoQueries
                .selectMegolmInbound(sessionId.value)
                .executeAsOneOrNull()
                ?.let { SerializedObject((it)) }
        }
    }

    override suspend fun startTransaction(action: suspend () -> Unit) {
        suspendCoroutine { continuation ->
            database.cryptoQueries.transaction {
                continuation.resume(this)
            }
        }
        action()
    }
}
