package app.dapk.engine.core

interface Base64 {
    fun encode(input: ByteArray): String
    fun decode(input: String): ByteArray
}