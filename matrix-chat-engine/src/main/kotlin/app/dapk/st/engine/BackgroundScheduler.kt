package app.dapk.st.engine

import app.dapk.st.matrix.common.JsonString

fun interface BackgroundScheduler {
    fun schedule(key: String, task: Task)
    data class Task(val type: String, val jsonPayload: JsonString)
}

