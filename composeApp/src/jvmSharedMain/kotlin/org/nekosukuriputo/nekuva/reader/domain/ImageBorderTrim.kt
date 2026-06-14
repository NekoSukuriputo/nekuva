package org.nekosukuriputo.nekuva.reader.domain

import coil3.Bitmap

/**
 * Trim near-uniform borders (white/black page margins) from a decoded page bitmap — Doki's
 * "Crop pages" feature. Returns the original bitmap when no meaningful border is detected.
 *
 * Platform actuals scan from the four edges inward and stop at the first non-uniform line, so the
 * common case (thin or no border) is cheap. Android reads the pixels in one bulk copy; Desktop uses
 * Skia's refcounted `extractSubset` for a zero-copy crop.
 */
expect fun trimImageBorders(bitmap: Bitmap): Bitmap
