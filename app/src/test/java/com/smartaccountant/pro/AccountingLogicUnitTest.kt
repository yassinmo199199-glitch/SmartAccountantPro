package com.smartaccountant.pro

import com.smartaccountant.pro.core.security.PinHasher
import com.smartaccountant.pro.core.sync.ConflictResolver
import com.smartaccountant.pro.core.sync.MergeDecision
import com.smartaccountant.pro.core.utils.ArabicTafqeet
import com.smartaccountant.pro.data.model.SyncStatus
import org.junit.Assert.*
import org.junit.Test

class AccountingLogicUnitTest {

    @Test
    fun pinHasher_verifiesCorrectPinAndRejectsWrongPin() {
        val hash = PinHasher.hashPin("4829")
        assertTrue(hash.contains(":"))
        assertFalse(hash.contains("4829"))
        assertTrue(PinHasher.verifyPin("4829", hash))
        assertFalse(PinHasher.verifyPin("1234", hash))
    }

    @Test
    fun arabicTafqeet_formatsAmountsCorrectly() {
        val text = ArabicTafqeet.convert(1250.0, "ريال", "فلس")
        assertTrue(text.contains("ألف"))
        assertTrue(text.contains("مائتان"))
        assertTrue(text.contains("خمسون"))
        assertTrue(text.endsWith("فقط لا غير"))
    }

    @Test
    fun conflictResolver_flagsConflictWhenLocalPendingAndRemoteIsNewer() {
        val decision = ConflictResolver.decide(
            localSyncStatus = SyncStatus.PENDING_UPDATE,
            localUpdatedAt = 1000L,
            remoteUpdatedAt = 2000L
        )
        assertEquals(MergeDecision.FLAG_CONFLICT, decision)
    }

    @Test
    fun conflictResolver_acceptsRemoteWhenLocalIsSynced() {
        val decision = ConflictResolver.decide(
            localSyncStatus = SyncStatus.SYNCED,
            localUpdatedAt = 1000L,
            remoteUpdatedAt = 2000L
        )
        assertEquals(MergeDecision.ACCEPT_REMOTE, decision)
    }
}
