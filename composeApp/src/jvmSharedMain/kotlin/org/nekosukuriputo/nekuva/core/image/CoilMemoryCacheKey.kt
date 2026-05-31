package org.nekosukuriputo.nekuva.core.image

import androidx.collection.ArrayMap
import coil3.memory.MemoryCache
import coil3.request.SuccessResult


class CoilMemoryCacheKey(
	val data: MemoryCache.Key
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as CoilMemoryCacheKey
		return data == other.data
	}

	override fun hashCode(): Int {
		return data.hashCode()
	}
}

