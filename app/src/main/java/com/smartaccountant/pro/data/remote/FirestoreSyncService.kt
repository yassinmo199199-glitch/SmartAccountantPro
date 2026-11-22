package com.smartaccountant.pro.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartaccountant.pro.core.session.SessionStore
import kotlinx.coroutines.tasks.await

class FirestoreSyncService(
    private val context: Context,
    private val authService: FirebaseAuthService
) {
    private val gson = Gson()
    private val sessionStore by lazy { SessionStore(context) }

    /** يُستدعى عند فشل أي listener مجموعة فرعية — يعرض الخطأ الحقيقي بدل ابتلاعه */
    var onSyncError: ((collection: String, message: String) -> Unit)? = null

    private fun resolveCompanyId(companyId: String): String? =
        companyId.trim().ifBlank { sessionStore.currentCompanyId?.takeIf { it.isNotBlank() } }

    private suspend fun ensureAnonymousAuthOrNull(): String? = try {
        authService.ensureAnonymousAuth()
    } catch (_: Exception) {
        null
    }

    fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun firestoreOrNull(): FirebaseFirestore? {
        if (!isFirebaseConfigured()) return null
        val uid = ensureAnonymousAuthOrNull() ?: return null
        if (uid.isBlank()) return null
        return try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    /** إرجاع معرف Firebase Auth الحالي (uid) أو null إذا لم تكن الجلسة جاهزة */
    suspend fun currentUid(): String? = try {
        authService.ensureAuthenticatedSession()
    } catch (_: Exception) {
        null
    }

    suspend fun <T> pushDocument(companyId: String, collection: String, docId: String, item: T): Boolean {
        return try {
            val cid = resolveCompanyId(companyId) ?: return false
            ensureAnonymousAuthOrNull() ?: return false
            val db = firestoreOrNull() ?: return false
            val json = gson.toJson(item)
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(json, mapType)
            db.collection("companies")
                .document(cid)
                .collection(collection)
                .document(docId)
                .set(map, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "pushDocument $collection/$docId failed: ${e.message}", e)
            // فشل سجل واحد لا يوقف مزامنة بقية السجلات — سيُعاد تلقائياً بعد 10 ثوانٍ
            false
        }
    }

    /** جلب مستند واحد من مجموعة فرعية companies/{companyId}/{collection}/{docId} */
    suspend fun <T> getDocument(companyId: String, collection: String, docId: String, clazz: Class<T>): T? {
        val cid = resolveCompanyId(companyId) ?: return null
        ensureAnonymousAuthOrNull() ?: return null
        val db = firestoreOrNull() ?: return null
        val snap = try {
            db.collection("companies").document(cid).collection(collection).document(docId).get().await()
        } catch (_: Exception) {
            return null
        }
        if (!snap.exists()) return null
        return try {
            gson.fromJson(gson.toJson(snap.data), clazz)
        } catch (_: Exception) {
            null
        }
    }

    /** جلب كل مستندات مجموعة فرعية مرة واحدة (بدون listener) */
    suspend fun <T> fetchCollection(companyId: String, collection: String, clazz: Class<T>): List<T> {
        val cid = resolveCompanyId(companyId) ?: return emptyList()
        ensureAnonymousAuthOrNull() ?: return emptyList()
        val db = firestoreOrNull() ?: return emptyList()
        val snap = try {
            db.collection("companies").document(cid).collection(collection).get().await()
        } catch (_: Exception) {
            return emptyList()
        }
        return snap.documents.mapNotNull { doc ->
            try {
                gson.fromJson(gson.toJson(doc.data), clazz)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun <T> fetchCollectionSince(companyId: String, collection: String, clazz: Class<T>, minTimestamp: Long): List<T> {
        val cid = resolveCompanyId(companyId) ?: return emptyList()
        ensureAnonymousAuthOrNull() ?: return emptyList()
        val db = firestoreOrNull() ?: return emptyList()
        val snap = try {
            db.collection("companies").document(cid).collection(collection)
                .whereGreaterThan("timestamp", minTimestamp)
                .get().await()
        } catch (_: Exception) {
            return emptyList()
        }
        return snap.documents.mapNotNull { doc ->
            try {
                gson.fromJson(gson.toJson(doc.data), clazz)
            } catch (_: Exception) {
                null
            }
        }
    }

    /** جلب وثيقة الشركة نفسها من companies/{companyId} */
    suspend fun <T> getCompanyDocument(companyId: String, clazz: Class<T>): T? {
        val cid = resolveCompanyId(companyId) ?: return null
        ensureAnonymousAuthOrNull() ?: return null
        val db = firestoreOrNull() ?: return null
        val snap = try {
            db.collection("companies").document(cid).get().await()
        } catch (_: Exception) {
            return null
        }
        if (!snap.exists()) return null
        return try {
            gson.fromJson(gson.toJson(snap.data), clazz)
        } catch (_: Exception) {
            null
        }
    }

    /** جلب مستند من مجموعة رئيسية مثل app_config/latest */
    suspend fun getRootDocumentMap(collection: String, docId: String): Map<String, Any?>? {
        ensureAnonymousAuthOrNull() ?: return null
        val db = firestoreOrNull() ?: return null
        val snap = try {
            db.collection(collection).document(docId).get().await()
        } catch (_: Exception) {
            return null
        }
        if (!snap.exists()) return null
        return snap.data
    }

    /** دفع وثيقة الشركة نفسها في companies/{companyId} */
    suspend fun pushCompanyDocument(companyId: String, item: Any): Boolean {
        return try {
            val cid = resolveCompanyId(companyId) ?: return false
            ensureAnonymousAuthOrNull() ?: return false
            val db = firestoreOrNull() ?: return false
            val json = gson.toJson(item)
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(json, mapType)
            db.collection("companies").document(cid).set(map, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "pushCompanyDocument $companyId failed: ${e.message}", e)
            false
        }
    }

    /** دفع مستند عضو في companies/{companyId}/members/{memberId} */
    suspend fun pushMemberDocument(companyId: String, memberId: String, item: Any): Boolean {
        return try {
            val cid = resolveCompanyId(companyId) ?: return false
            ensureAnonymousAuthOrNull() ?: return false
            val db = firestoreOrNull() ?: return false
            val json = gson.toJson(item)
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(json, mapType)
            db.collection("companies").document(cid).collection("members").document(memberId)
                .set(map, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "pushMemberDocument $memberId failed: ${e.message}", e)
            false
        }
    }

    /** حذف مستند من مجموعة فرعية — يُستخدم لسحب عضوية مؤقتة عند فشل التحقق من PIN */
    suspend fun deleteDocument(companyId: String, collection: String, docId: String): Boolean {
        return try {
            val cid = resolveCompanyId(companyId) ?: return false
            ensureAnonymousAuthOrNull() ?: return false
            val db = firestoreOrNull() ?: return false
            db.collection("companies").document(cid).collection(collection).document(docId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteDocument $collection/$docId failed: ${e.message}", e)
            false
        }
    }

    suspend fun <T> listenCollection(
        companyId: String,
        collection: String,
        clazz: Class<T>,
        onItemsChanged: (List<T>) -> Unit
    ): ListenerRegistration? {
        if (!isFirebaseConfigured()) return null
        val cid = resolveCompanyId(companyId) ?: return null
        ensureAnonymousAuthOrNull() ?: return null
        val db = try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            return null
        }
        return db.collection("companies")
            .document(cid)
            .collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listener $collection error: ${error.message}", error)
                    onSyncError?.invoke(collection, error.message ?: "خطأ غير معروف")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val json = gson.toJson(doc.data)
                        gson.fromJson(json, clazz)
                    } catch (_: Exception) {
                        null
                    }
                }
                // تمرير القائمة دائماً حتى لو كانت فارغة أو تحتوي على سجلات محذوفة ناعماً
                onItemsChanged(items)
            }
    }

    companion object {
        private const val TAG = "FirestoreSync"
    }
}
