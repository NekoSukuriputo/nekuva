package org.nekosukuriputo.nekuva.core.db

import org.nekosukuriputo.nekuva.core.db.MangaDatabase

suspend fun <T> MangaDatabase.withTransactionKmp(block: suspend () -> T): T {
    // For now, execute without a transaction wrapper on KMP.
    // Room KMP is in alpha/beta, withTransaction might be missing in desktop target.
    return block()
}
