package org.nekosukuriputo.nekuva.reader.domain

/** Decode just the pixel dimensions (width, height) of an encoded image, or null on failure. */
expect fun decodeImageBounds(bytes: ByteArray): Pair<Int, Int>?
