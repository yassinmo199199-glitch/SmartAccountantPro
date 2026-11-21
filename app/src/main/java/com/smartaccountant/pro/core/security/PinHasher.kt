package com.smartaccountant.pro.core.security

import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
    private val random = SecureRandom()

    fun hashPin(rawPin: String): String {
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val digestHex = sha256("$saltHex:${rawPin.trim()}")
        return "$saltHex:$digestHex"
    }

    fun verifyPin(rawPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || !storedHash.contains(":")) return false
        val parts = storedHash.split(":", limit = 2)
        val saltHex = parts[0]
        val expectedDigest = parts[1]
        val actualDigest = sha256("$saltHex:${rawPin.trim()}")
        return MessageDigest.isEqual(
            expectedDigest.toByteArray(Charsets.UTF_8),
            actualDigest.toByteArray(Charsets.UTF_8)
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
