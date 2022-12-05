package app.dapk.engine.core.extensions

interface ErrorTracker {
    fun track(throwable: Throwable, extra: String = "")
}

fun <T> ErrorTracker.nullAndTrack(throwable: Throwable, extra: String = ""): T? {
    this.track(throwable, extra)
    return null
}