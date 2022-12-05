package app.dapk.engine.core.extensions

inline fun <T, R> List<T>.ifNotEmpty(transform: (List<T>) -> List<R>) = if (this.isEmpty()) emptyList() else transform(this)
