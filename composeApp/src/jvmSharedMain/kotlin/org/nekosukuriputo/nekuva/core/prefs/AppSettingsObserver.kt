package org.nekosukuriputo.nekuva.core.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Emits the current value of [key], then re-emits whenever it changes. Backed by [keyChangeFlow]
 * (an ObservableSettings key listener, which fires for that key regardless of stored type), so it
 * works for boolean keys too. The previous implementation collected an empty flow and never updated,
 * which silently froze every consumer (e.g. the Explore source list ignored enable/disable toggles).
 */
fun <T> AppSettings.observeAsFlow(key: String, valueProducer: AppSettings.() -> T): Flow<T> =
	keyChangeFlow(key)
		.map { valueProducer() }
		.distinctUntilChanged()

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
fun <T> AppSettings.observeAsStateFlow(
	scope: CoroutineScope,
	key: String,
	valueProducer: AppSettings.() -> T,
): StateFlow<T> = keyChangeFlow(key)
	.map { valueProducer() }
	.stateIn(scope, SharingStarted.Eagerly, valueProducer())

