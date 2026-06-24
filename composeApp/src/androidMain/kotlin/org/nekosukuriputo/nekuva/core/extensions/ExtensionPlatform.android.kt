package org.nekosukuriputo.nekuva.core.extensions

import android.content.Context
import org.koin.core.context.GlobalContext
import java.io.File

actual fun extensionsDir(): File =
    File(GlobalContext.get().get<Context>().filesDir, "extensions").apply { mkdirs() }

actual val extensionPlatformKey: String = "android"

// Android needs a dexed bundle (DexClassLoader) before it can load/import — Step 3. Disabled for now.
actual val supportsExtensionImport: Boolean = false

actual suspend fun pickExtensionJar(): String? = null
