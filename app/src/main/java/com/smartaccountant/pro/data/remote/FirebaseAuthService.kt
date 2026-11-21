package com.smartaccountant.pro.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class FirebaseAuthService(private val context: Context) {

    fun isFirebaseReady(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يضمن وجود جلسة Firebase Auth نشطة عبر المصادقة المجهولة (Anonymous).
     * يفحص currentUser أولاً، وإذا كان null يستدعي signInAnonymously().await().
     * @return معرف المستخدم (uid) أو null إذا لم يكن Firebase مهيأً أو فشلت المصادقة
     */
    suspend fun ensureAnonymousAuth(): String? {
        if (!isFirebaseReady()) return null
        return try {
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.uid?.let { return it }
            val result = auth.signInAnonymously().await()
            result.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "ensureAnonymousAuth failed: ${e.message}", e)
            null
        }
    }

    /**
     * يضمن وجود جلسة مصادقة نشطة (request.auth != null) قبل أي قراءة أو كتابة في Firestore
     */
    suspend fun ensureAuthenticatedSession(email: String? = null, password: String? = null): String? {
        if (!isFirebaseReady()) return null
        return try {
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.uid?.let { return it }

            if (!email.isNullOrBlank() && !password.isNullOrBlank() && password.length >= 6) {
                try {
                    val res = auth.signInWithEmailAndPassword(email, password).await()
                    return res.user?.uid
                } catch (_: Exception) {
                    // التراجع للمصادقة المجهولة المرتبطة بمعرف المستخدم الداخلي
                }
            }

            val anonRes = auth.signInAnonymously().await()
            anonRes.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "ensureAuthenticatedSession failed: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "FirebaseAuth"
    }
}
