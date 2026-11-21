package com.smartaccountant.pro.ui.components

import com.google.firebase.FirebaseException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UiErrorMessages {
    fun fromThrowable(error: Throwable, fallback: String): String {
        val causes = generateSequence(error) { it.cause }
        if (causes.any {
                it is FirebaseException || it is UnknownHostException ||
                    it is SocketException || it is SocketTimeoutException
            }
        ) {
            return "تعذر الاتصال — تحقق من الإنترنت"
        }

        val message = error.message?.trim().orEmpty()
        return if (message.any { it in '\u0600'..'\u06FF' }) message else fallback
    }
}
