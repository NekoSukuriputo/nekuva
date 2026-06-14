package org.nekosukuriputo.nekuva.reader.domain

/**
 * Platform sink for a decoded reader page. KMP replacement for Doki's Android-only `PageSaveHelper`
 * SAF flow: Android writes to the MediaStore (Pictures) and shares via a content Uri; Desktop writes
 * to a Nekuva folder under the user's Pictures and reveals it. Provided per-platform via Koin.
 */
interface PagePersister {

	/** Persist [bytes] under [fileName]; returns a human-readable location, or null on failure. */
	suspend fun savePage(bytes: ByteArray, fileName: String, mimeType: String): String?

	/**
	 * Share [bytes] via the platform share mechanism (Desktop: save + reveal in folder). Returns the
	 * human-readable saved location (so the caller can report it), or null on failure.
	 */
	suspend fun sharePage(bytes: ByteArray, fileName: String, mimeType: String): String?
}
