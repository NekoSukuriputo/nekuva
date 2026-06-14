package org.nekosukuriputo.nekuva.sync.domain

import okio.IOException

/** Raised when the sync server rejects a request (auth failure, version mismatch, etc.). */
class SyncApiException(
	message: String,
	val code: Int,
) : IOException(message)
