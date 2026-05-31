package org.nekosukuriputo.nekuva.core.util

import java.util.LinkedHashMap

class SynchronizedSieveCache<K : Any, V : Any>(
	private val maxSize: Int,
) {
	private val lock = Any()
	private val delegate = object : LinkedHashMap<K, V>(0, 0.75f, true) {
		override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
			return size > maxSize
		}
	}

	operator fun get(key: K): V? = synchronized(lock) {
		delegate[key]
	}

	fun put(key: K, value: V): V? = synchronized(lock) {
		delegate.put(key, value)
	}

	fun remove(key: K): V? = synchronized(lock) {
		delegate.remove(key)
	}

	fun evictAll() = synchronized(lock) {
		delegate.clear()
	}

	fun trimToSize(maxSize: Int) = synchronized(lock) {
		while (delegate.size > maxSize && delegate.isNotEmpty()) {
			val key = delegate.keys.firstOrNull()
			if (key != null) delegate.remove(key)
		}
	}

	fun removeIf(predicate: (K, V) -> Boolean) = synchronized(lock) {
		val iterator = delegate.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			if (predicate(entry.key, entry.value)) {
				iterator.remove()
			}
		}
	}
}
