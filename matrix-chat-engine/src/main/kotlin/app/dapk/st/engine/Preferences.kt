package app.dapk.st.engine

interface Preferences {
    suspend fun store(key: String, value: String)
    suspend fun readString(key: String): String?
    suspend fun clear()
    suspend fun remove(key: String)
}