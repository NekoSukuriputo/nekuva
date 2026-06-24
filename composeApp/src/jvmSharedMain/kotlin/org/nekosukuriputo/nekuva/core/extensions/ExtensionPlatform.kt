package org.nekosukuriputo.nekuva.core.extensions

import java.io.File

/** Where a downloaded/imported extension bundle is stored (created if missing). */
expect fun extensionsDir(): File

/** The artifact key this platform downloads from the catalog (`index.json` `artifacts.<key>`). */
expect val extensionPlatformKey: String

/** Whether this platform can import an extension from a local file (Desktop yes; Android via dex later). */
expect val supportsExtensionImport: Boolean

/** Pick a local extension `.jar` to import, or null if cancelled/unsupported. */
expect suspend fun pickExtensionJar(): String?
