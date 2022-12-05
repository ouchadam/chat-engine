package app.dapk.st.olm

import app.dapk.st.matrix.common.Curve25519
import app.dapk.st.matrix.common.RoomId
import app.dapk.st.matrix.common.SessionId

interface OlmPersistence {
    suspend fun read(): String?
    suspend fun persist(olmAccount: SerializedObject)
    suspend fun readOutbound(roomId: RoomId): Pair<Long, String>?
    suspend fun persistOutbound(roomId: RoomId, creationTimestampUtc: Long, outboundGroupSession: SerializedObject)
    suspend fun persistSession(identity: Curve25519, sessionId: SessionId, olmSession: SerializedObject)
    suspend fun readSessions(identities: List<Curve25519>): List<Pair<Curve25519, String>>?
    suspend fun persist(sessionId: SessionId, inboundGroupSession: SerializedObject)
    suspend fun readInbound(sessionId: SessionId): SerializedObject?
    suspend fun startTransaction(action: suspend () -> Unit)
}

@JvmInline
value class SerializedObject(val value: String)