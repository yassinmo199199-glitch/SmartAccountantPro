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
     * يضمن وجود جلسة مصادقة نشطة موثقة بـ Firebase Auth بالإيميل أو المصادقة المجهولة
     */
    suspend fun ensureAuthenticatedSession(email: String? = null, password: String? = null): String? {
        if (!isFirebaseReady()) return null
        return try {
            val auth = FirebaseAuth.getInstance()

            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                val cleanEmail = email.trim()
                val cleanPassword = password.trim()
                try {
                    // تسجيل الخروج من الجلسة المجهولة السابقة للتوثيق المباشر بالإيميل
                    if (auth.currentUser?.isAnonymous == true) {
                        auth.signOut()
                    }
                    val res = auth.signInWithEmailAndPassword(cleanEmail, cleanPassword).await()
                    val uid = res.user?.uid
                    if (!uid.isNullOrBlank()) return uid
                } catch (e: Exception) {
                    Log.w(TAG, "signInWithEmailAndPassword failed for $cleanEmail: ${e.message}")
                    try {
                        val createRes = auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).await()
                        val uid = createRes.user?.uid
                        if (!uid.isNullOrBlank()) return uid
                    } catch (createEx: Exception) {
                        Log.w(TAG, "createUserWithEmailAndPassword failed: ${createEx.message}")
                    }
                }
            }

            auth.currentUser?.uid?.let { return it }
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
