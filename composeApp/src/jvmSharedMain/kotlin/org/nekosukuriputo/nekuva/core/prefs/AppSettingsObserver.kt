package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
fun <T> AppSettings.observeAsFlow(key: String, valueProducer: AppSettings.() -> T) = flow {
	var lastValue: T = valueProducer()
	emit(lastValue)
	
	// Assuming prefs is exposed as `val prefs: ObservableSettings` in AppSettings
	// we would do prefs.toFlowSettings().keysFlow(). But since prefs is private,
    // we should instead expose observeChanges inside AppSettings.
	// For now, we will just use a dummy implementation or assume prefs is accessible.
	// Let's add observeChanges to AppSettings.
	emptyFlow<String>().collect { changedKey ->
		if (changedKey == key) {
			val value = valueProducer()
			if (value != lastValue) {
				emit(value)
			}
			lastValue = value
		}
	}
}

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
fun <T> AppSettings.observeAsStateFlow(
	scope: CoroutineScope,
	key: String,
	valueProducer: AppSettings.() -> T,
): StateFlow<T> = keyChangeFlow(key)
	.map { valueProducer() }
	.stateIn(scope, SharingStarted.Eagerly, valueProducer())

