package com.smartaccountant.pro.core.sync

import com.smartaccountant.pro.data.model.SyncStatus

enum class MergeDecision {
    ACCEPT_REMOTE,
    KEEP_LOCAL_PENDING,
    FLAG_CONFLICT
}

object ConflictResolver {
    fun decide(
        localSyncStatus: SyncStatus?,
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long
    ): MergeDecision {
        if (localSyncStatus == null || localUpdatedAt == null) {
            return MergeDecision.ACCEPT_REMOTE
        }
        return when (localSyncStatus) {
            SyncStatus.SYNCED -> {
                if (remoteUpdatedAt >= localUpdatedAt) MergeDecision.ACCEPT_REMOTE
                else MergeDecision.KEEP_LOCAL_PENDING
            }
            SyncStatus.PENDING_CREATE,
            SyncStatus.PENDING_UPDATE,
            SyncStatus.PENDING_DELETE -> {
                if (remoteUpdatedAt > localUpdatedAt) MergeDecision.FLAG_CONFLICT
                else MergeDecision.KEEP_LOCAL_PENDING
            }
            SyncStatus.CONFLICT -> MergeDecision.FLAG_CONFLICT
        }
    }
}
