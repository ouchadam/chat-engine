package app.dapk.st.matrix.crypto.internal

import app.dapk.engine.core.Base64
import app.dapk.st.matrix.crypto.Crypto
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val CRYPTO_BUFFER_SIZE = 32 * 1024
private const val CIPHER_ALGORITHM = "AES/CTR/NoPadding"
private const val SECRET_KEY_SPEC_ALGORITHM = "AES"
private const val MESSAGE_DIGEST_ALGORITHM = "SHA-256"

class MediaEncrypter(private val base64: Base64) {

    fun encrypt(input: InputStream): Crypto.MediaEncryptionResult {
        val secureRandom = SecureRandom()
        val initVectorBytes = ByteArray(16) { 0.toByte() }

        val ivRandomPart = ByteArray(8)
        secureRandom.nextBytes(ivRandomPart)

        System.arraycopy(ivRandomPart, 0, initVectorBytes, 0, ivRandomPart.size)

        val key = ByteArray(32)
        secureRandom.nextBytes(key)

        val messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM)

        val outputFile = File.createTempFile("_encrypt-${UUID.randomUUID()}", ".png")

        outputFile.outputStream().use { s ->
            val encryptCipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val secretKeySpec = SecretKeySpec(key, SECRET_KEY_SPEC_ALGORITHM)
            val ivParameterSpec = IvParameterSpec(initVectorBytes)
            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

            val data = ByteArray(CRYPTO_BUFFER_SIZE)
            var read: Int
            var encodedBytes: ByteArray

            input.use { inputStream ->
                read = inputStream.read(data)
                var totalRead = read
                while (read != -1) {
                    encodedBytes = encryptCipher.update(data, 0, read)
                    messageDigest.update(encodedBytes, 0, encodedBytes.size)
                    s.write(encodedBytes)
                    read = inputStream.read(data)
                    totalRead += read
                }
            }

            encodedBytes = encryptCipher.doFinal()
            messageDigest.update(encodedBytes, 0, encodedBytes.size)
            s.write(encodedBytes)
        }

        return Crypto.MediaEncryptionResult(
            uri = outputFile.toURI(),
            contentLength = outputFile.length(),
            algorithm = "A256CTR",
            ext = true,
            keyOperations = listOf("encrypt", "decrypt"),
            kty = "oct",
            k = base64ToBase64Url(base64.encode(key)),
            iv = base64.encode(initVectorBytes).replace("\n", "").replace("=", ""),
            hashes = mapOf("sha256" to base64ToUnpaddedBase64(base64.encode(messageDigest.digest()))),
            v = "v2"
        )
    }
}

private fun base64ToBase64Url(base64: String): String {
    return base64.replace("\n".toRegex(), "")
        .replace("\\+".toRegex(), "-")
        .replace('/', '_')
        .replace("=", "")
}

private fun base64ToUnpaddedBase64(base64: String): String {
    return base64.replace("\n".toRegex(), "")
        .replace("=", "")
}