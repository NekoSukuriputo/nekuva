package org.nekosukuriputo.nekuva.core.extensions

// Android runtime loading needs a DEXED bundle via DexClassLoader (Step 3) — not implemented yet, so the
// app uses the bundled baseline parsers. Returns null until the dexed artifact + loader land.
actual fun loadExtension(path: String): LoadedExtension? = null
