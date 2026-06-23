package org.nekosukuriputo.nekuva.core.prefs


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

import org.nekosukuriputo.nekuva.core.model.ZoomMode






import org.nekosukuriputo.nekuva.explore.data.SourcesSortOrder
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.SortOrder

import org.nekosukuriputo.nekuva.parsers.util.mapNotNullToSet
import org.nekosukuriputo.nekuva.parsers.util.mapToSet
import org.nekosukuriputo.nekuva.parsers.util.nullIfEmpty

import java.io.File
import java.net.Proxy
import kotlin.collections.Set
import java.util.concurrent.TimeUnit

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.set
import com.russhwolf.settings.coroutines.toFlowSettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class AppSettings(private val prefs: ObservableSettings) {

	// DNS-over-HTTPS provider (Doki `doh`). Stored as the entry index (0=None…4=0ms) by the settings UI.
	val dnsOverHttps: org.nekosukuriputo.nekuva.core.network.DoHProvider
		get() {
			val idx = prefs.getStringOrNull(KEY_DOH)?.toIntOrNull() ?: 0
			return org.nekosukuriputo.nekuva.core.network.DoHProvider.entries.getOrElse(idx) {
				org.nekosukuriputo.nekuva.core.network.DoHProvider.NONE
			}
		}

	// TODO: connectivityManager
	private val mangaListBadgesDefault = setOf("1", "2", "4")

	var listMode: ListMode
		get() = prefs.getEnum(KEY_LIST_MODE, ListMode.GRID)
		set(value) = prefs.putEnum(KEY_LIST_MODE, value)

	// -1 = follow system, 1 = light, 2 = dark (AppCompatDelegate night-mode convention).
	var theme: Int
		get() = prefs.getStringOrNull(KEY_THEME)?.toIntOrNull() ?: -1
		set(value) = prefs.putString(KEY_THEME, value.toString())

	var colorScheme: ColorScheme
		get() = prefs.getEnum(KEY_COLOR_THEME, ColorScheme.default)
		set(value) = prefs.putEnum(KEY_COLOR_THEME, value)

	var isAmoledTheme: Boolean
		get() = prefs.getBoolean(KEY_THEME_AMOLED, false)
		set(value) = prefs.putBoolean(KEY_THEME_AMOLED, value)

	/** Live theme observers for [org.nekosukuriputo.nekuva.App] so theme/AMOLED changes apply instantly. */
	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeTheme(): kotlinx.coroutines.flow.Flow<Int> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_THEME).map { it?.toIntOrNull() ?: -1 }

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeAmoled(): kotlinx.coroutines.flow.Flow<Boolean> =
		prefs.toFlowSettings().getBooleanFlow(KEY_THEME_AMOLED, false)

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeColorScheme(): kotlinx.coroutines.flow.Flow<String> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_COLOR_THEME).map { it ?: ColorScheme.default.name }

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeAppLocale(): kotlinx.coroutines.flow.Flow<String> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_APP_LOCALE).map { it ?: "" }

	/** Live manga-list appearance observers (Appearance settings → list screens, Doki parity). */
	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeListModeOrNull(key: String): kotlinx.coroutines.flow.Flow<ListMode?> =
		prefs.toFlowSettings().getStringOrNullFlow(key).map { name ->
			name?.let { runCatching { ListMode.valueOf(it) }.getOrNull() }
		}

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeGridSize(): kotlinx.coroutines.flow.Flow<Int> =
		prefs.toFlowSettings().getIntFlow(KEY_GRID_SIZE, 100)

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeInt(key: String, default: Int): kotlinx.coroutines.flow.Flow<Int> =
		prefs.toFlowSettings().getIntFlow(key, default)

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeBoolean(key: String, default: Boolean): kotlinx.coroutines.flow.Flow<Boolean> =
		prefs.toFlowSettings().getBooleanFlow(key, default)

	/** Emits each time the given (string-backed) preference key changes. Used by [observeAsStateFlow]. */
	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun keyChangeFlow(key: String): kotlinx.coroutines.flow.Flow<Unit> =
		prefs.toFlowSettings().getStringOrNullFlow(key).map { }

	// --- Generic typed preference access for the settings UI (key constants are in the companion below) ---
	fun prefBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
	fun prefInt(key: String, default: Int): Int = prefs.getInt(key, default)
	fun prefString(key: String, default: String): String = prefs.getStringOrNull(key) ?: default
	fun prefStringSet(key: String, default: Set<String>): Set<String> = prefs.getStringSet(key, default)

	fun setPref(key: String, value: Boolean) = prefs.putBoolean(key, value)
	fun setPref(key: String, value: Int) = prefs.putInt(key, value)
	fun setPref(key: String, value: String) = prefs.putString(key, value)
	fun setPref(key: String, value: Set<String>) = prefs.putStringSet(key, value)

	var mainNavItems: List<NavItem>
		get() = parseNavItems(prefs.getStringOrNull(KEY_NAV_MAIN))
		set(value) {
			prefs.putString(KEY_NAV_MAIN, value.joinToString(",") { it.name })
		}

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeNavItems(): kotlinx.coroutines.flow.Flow<List<NavItem>> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_NAV_MAIN).map { parseNavItems(it) }

	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeScreenshotsPolicy(): kotlinx.coroutines.flow.Flow<ScreenshotsPolicy> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_SCREENSHOTS_POLICY)
			.map { name -> name?.let { runCatching { ScreenshotsPolicy.valueOf(it) }.getOrNull() } ?: ScreenshotsPolicy.ALLOW }

	/** True while an app-lock password is set (drives the Appearance toggle live). */
	@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
	fun observeAppPasswordSet(): kotlinx.coroutines.flow.Flow<Boolean> =
		prefs.toFlowSettings().getStringOrNullFlow(KEY_APP_PASSWORD).map { it != null }

	private fun parseNavItems(raw: String?): List<NavItem> {
		val parsed = raw?.split(',')?.mapNotNull { x -> NavItem.entries.find { it.name == x } }
		return parsed?.ifEmpty { null } ?: NAV_ITEMS_DEFAULT
	}

	val isNavLabelsVisible: Boolean
		get() = prefs.getBoolean(KEY_NAV_LABELS, true)

	val isNavBarPinned: Boolean
		get() = prefs.getBoolean(KEY_NAV_PINNED, false)

	val isMainFabEnabled: Boolean
		get() = prefs.getBoolean(KEY_MAIN_FAB, true)

	var gridSize: Int
		get() = prefs.getInt(KEY_GRID_SIZE, 100)
		set(value) = prefs.putInt(KEY_GRID_SIZE, value)

	/** Read/write a list mode by key (global or per-screen), used by the "list options" sheet. */
	fun getListMode(key: String): ListMode = prefs.getEnum(key, listMode)
	fun setListMode(key: String, mode: ListMode) = prefs.putEnum(key, mode)

	var gridSizePages: Int
		get() = prefs.getInt(KEY_GRID_SIZE_PAGES, 100)
		set(value) = prefs.putInt(KEY_GRID_SIZE_PAGES, value)

	val isQuickFilterEnabled: Boolean
		get() = prefs.getBoolean(KEY_QUICK_FILTER, true)

	val isDescriptionExpanded: Boolean
		get() = !prefs.getBoolean(KEY_COLLAPSE_DESCRIPTION, true)

	var historyListMode: ListMode
		get() = prefs.getEnum(KEY_LIST_MODE_HISTORY, listMode)
		set(value) = prefs.putEnum(KEY_LIST_MODE_HISTORY, value)

	var suggestionsListMode: ListMode
		get() = prefs.getEnum(KEY_LIST_MODE_SUGGESTIONS, listMode)
		set(value) = prefs.putEnum(KEY_LIST_MODE_SUGGESTIONS, value)

	var favoritesListMode: ListMode
		get() = prefs.getEnum(KEY_LIST_MODE_FAVORITES, listMode)
		set(value) = prefs.putEnum(KEY_LIST_MODE_FAVORITES, value)

	val isTagsWarningsEnabled: Boolean
		get() = prefs.getBoolean(KEY_TAGS_WARNINGS, true)

	var isNsfwContentDisabled: Boolean
		get() = prefs.getBoolean(KEY_DISABLE_NSFW, false)
		set(value) = prefs.putBoolean(KEY_DISABLE_NSFW, value)

	var appLocales: String
		get() {
			val raw = prefs.getStringOrNull(KEY_APP_LOCALE)
			return raw ?: ""
		}
		set(value) {
			prefs.putString(KEY_APP_LOCALE, value)
		}

	var isReaderDoubleOnLandscape: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_PAGES, false)
		set(value) = prefs.putBoolean(KEY_READER_DOUBLE_PAGES, value)

    var isReaderDoubleOnFoldable: Boolean
        get() = prefs.getBoolean(KEY_READER_DOUBLE_FOLDABLE, false)
        set(value) = prefs.putBoolean(KEY_READER_DOUBLE_FOLDABLE, value)

	
	var readerDoublePagesSensitivity: Float
		get() = prefs.getFloat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, 0.5f)
		set( value) = prefs.putFloat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, value)

	val readerScreenOrientation: Int
		get() = prefs.getStringOrNull(KEY_READER_ORIENTATION)?.toIntOrNull()
			?: -1

	val isReaderVolumeButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_VOLUME_BUTTONS, false)

	val isReaderZoomButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_ZOOM_BUTTONS, false)

	val isReaderControlAlwaysLTR: Boolean
		get() = prefs.getBoolean(KEY_READER_CONTROL_LTR, false)

	val isReaderNavigationInverted: Boolean
		get() = prefs.getBoolean(KEY_READER_NAVIGATION_INVERTED, false)

	val isReaderFullscreenEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_FULLSCREEN, true)

	val isReaderOptimizationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_OPTIMIZE, false)

	// Global reader colour filter (per-manga override lives in MangaDataRepository). Mirrors Doki.
	var readerColorFilter: org.nekosukuriputo.nekuva.reader.domain.ReaderColorFilter
		get() = org.nekosukuriputo.nekuva.reader.domain.ReaderColorFilter(
			brightness = prefs.getFloat(KEY_CF_BRIGHTNESS, 0f),
			contrast = prefs.getFloat(KEY_CF_CONTRAST, 0f),
			isInverted = prefs.getBoolean(KEY_CF_INVERTED, false),
			isGrayscale = prefs.getBoolean(KEY_CF_GRAYSCALE, false),
			isBookBackground = prefs.getBoolean(KEY_CF_BOOK, false),
		)
		set(value) {
			prefs.putFloat(KEY_CF_BRIGHTNESS, value.brightness)
			prefs.putFloat(KEY_CF_CONTRAST, value.contrast)
			prefs.putBoolean(KEY_CF_INVERTED, value.isInverted)
			prefs.putBoolean(KEY_CF_GRAYSCALE, value.isGrayscale)
			prefs.putBoolean(KEY_CF_BOOK, value.isBookBackground)
		}

	val readerControls: Set<ReaderControl>
		// The settings UI stores controls as ordinal indices ("0".."7"); map by ordinal. Fall back to
		// Doki's DEFAULT whenever the result is empty (unset, or all unchecked) so the reader always has
		// its core controls — most importantly the chapters button.
		get() = prefs.getStringSet(KEY_READER_CONTROLS, emptySet())
			.mapNotNullTo(mutableSetOf<ReaderControl>()) { v -> ReaderControl.entries.getOrNull(v.toIntOrNull() ?: -1) }
			.ifEmpty { ReaderControl.DEFAULT }

	val isOfflineCheckDisabled: Boolean
		get() = prefs.getBoolean(KEY_OFFLINE_DISABLED, false)

	private val _isAllFavouritesVisibleFlow = MutableStateFlow(prefs.getBoolean(KEY_ALL_FAVOURITES_VISIBLE, true))
	val isAllFavouritesVisibleFlow: StateFlow<Boolean> = _isAllFavouritesVisibleFlow.asStateFlow()

	var isAllFavouritesVisible: Boolean
		get() = prefs.getBoolean(KEY_ALL_FAVOURITES_VISIBLE, true)
		set(value) {
            prefs.putBoolean(KEY_ALL_FAVOURITES_VISIBLE, value)
            _isAllFavouritesVisibleFlow.value = value
        }

	val isTrackerEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_ENABLED, true)

	val isTrackerWifiOnly: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_WIFI_ONLY, false)

	val trackerFrequencyFactor: Float
		get() = prefs.getStringOrNull(KEY_TRACKER_FREQUENCY)?.toFloatOrNull() ?: 1f

	val isTrackerNotificationsEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NOTIFICATIONS, true)

	val isTrackerNsfwDisabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NO_NSFW, false)

	val trackerDownloadStrategy: TrackerDownloadStrategy
		get() = prefs.getEnum(KEY_TRACKER_DOWNLOAD, TrackerDownloadStrategy.DISABLED)

	var notificationSound: String
		get() = prefs.getStringOrNull(KEY_NOTIFICATIONS_SOUND)
			?: "content://settings/system/notification_sound"
		set(value) = prefs.putString(KEY_NOTIFICATIONS_SOUND, value)

	val notificationVibrate: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_VIBRATE, false)

	val notificationLight: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_LIGHT, true)

	val readerAnimation: ReaderAnimation
		get() = prefs.getEnum(KEY_READER_ANIMATION, ReaderAnimation.DEFAULT)

	val readerBackground: ReaderBackground
		get() = prefs.getEnum(KEY_READER_BACKGROUND, ReaderBackground.DEFAULT)

	var defaultReaderMode: ReaderMode
		get() = prefs.getEnum(KEY_READER_MODE, ReaderMode.WEBTOON)
		set(value) = prefs.putEnum(KEY_READER_MODE, value)

	val isReaderModeDetectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MODE_DETECT, true)

	var isHistoryGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_HISTORY_GROUPING, true)
		set(value) = prefs.putBoolean(KEY_HISTORY_GROUPING, value)

	var isUpdatedGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_UPDATED_GROUPING, true)
		set(value) = prefs.putBoolean(KEY_UPDATED_GROUPING, value)

	var isFeedHeaderVisible: Boolean
		get() = prefs.getBoolean(KEY_FEED_HEADER, true)
		set(value) = prefs.putBoolean(KEY_FEED_HEADER, value)

	val progressIndicatorMode: ProgressIndicatorMode
		get() = prefs.getEnum(KEY_PROGRESS_INDICATORS, ProgressIndicatorMode.PERCENT_READ)

	var incognitoModeForNsfw: TriStateOption
		get() = prefs.getEnum(KEY_INCOGNITO_NSFW, TriStateOption.ASK)
		set(value) = prefs.putEnum(KEY_INCOGNITO_NSFW, value)

	var isIncognitoModeEnabled: Boolean
		get() = prefs.getBoolean(KEY_INCOGNITO_MODE, false)
		set(value) = prefs.putBoolean(KEY_INCOGNITO_MODE, value)

	val isReaderMultiTaskEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MULTITASK, false)

	var isChaptersReverse: Boolean
		get() = prefs.getBoolean(KEY_REVERSE_CHAPTERS, false)
		set(value) = prefs.putBoolean(KEY_REVERSE_CHAPTERS, value)

	var isChaptersGridView: Boolean
		get() = prefs.getBoolean(KEY_GRID_VIEW_CHAPTERS, false)
		set(value) = prefs.putBoolean(KEY_GRID_VIEW_CHAPTERS, value)

	val zoomMode: ZoomMode
		get() = prefs.getEnum(KEY_ZOOM_MODE, ZoomMode.FIT_CENTER)

	val trackSources: Set<String>
		get() = prefs.getStringSet(KEY_TRACK_SOURCES, emptySet()) ?: setOf(TRACK_FAVOURITES)

	var appPassword: String?
		get() = prefs.getStringOrNull(KEY_APP_PASSWORD)
		set(value) = if (value != null) prefs.putString(KEY_APP_PASSWORD, value) else prefs.remove(KEY_APP_PASSWORD)

	var isAppPasswordNumeric: Boolean
		get() = prefs.getBoolean(KEY_APP_PASSWORD_NUMERIC, false)
		set(value) = prefs.putBoolean(KEY_APP_PASSWORD_NUMERIC, value)

	val searchSuggestionTypes: Set<SearchSuggestionType>
		get() {
			// Absent key = ALL types enabled (Doki default). The old `?:` fallback never fired
			// because mapNotNullTo never returns null, which silently disabled every suggestion type.
			if (prefs.getStringOrNull(KEY_SEARCH_SUGGESTION_TYPES) == null) {
				return SearchSuggestionType.entries.toSet()
			}
			return prefs.getStringSet(KEY_SEARCH_SUGGESTION_TYPES, emptySet())
				.mapNotNullTo(mutableSetOf()) { x ->
					SearchSuggestionType.entries.find { it.name == x }
				}
		}

	var isBiometricProtectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_PROTECT_APP_BIOMETRIC, true)
		set(value) = prefs.putBoolean(KEY_PROTECT_APP_BIOMETRIC, value)

	val isMirrorSwitchingEnabled: Boolean
		get() = prefs.getBoolean(KEY_MIRROR_SWITCHING, false)

	val isExitConfirmationEnabled: Boolean
		get() = prefs.getBoolean(KEY_EXIT_CONFIRM, false)

	val isDynamicShortcutsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SHORTCUTS, true)

	val isUnstableUpdatesAllowed: Boolean
		get() = prefs.getBoolean(KEY_UPDATES_UNSTABLE, false)

	val isPagesTabEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_TAB, true)

	val defaultDetailsTab: Int
		get() = if (isPagesTabEnabled) {
			val raw = prefs.getStringOrNull(KEY_DETAILS_TAB)?.toIntOrNull() ?: -1
			if (raw == -1) {
				lastDetailsTab
			} else {
				raw
			}.coerceIn(0, 2)
		} else {
			0
		}

	var lastDetailsTab: Int
		get() = prefs.getInt(KEY_DETAILS_LAST_TAB, 0)
		set(value) = prefs.putInt(KEY_DETAILS_LAST_TAB, value)

	val isContentPrefetchEnabled: Boolean
		get() {
			if (false /*isBackgroundNetworkRestricted*/) {
				return false
			}
			val policy =
				NetworkPolicy.from(prefs.getStringOrNull(KEY_PREFETCH_CONTENT), NetworkPolicy.NONE)
			return policy.isNetworkAllowed()
		}

	var sourcesSortOrder: SourcesSortOrder
		get() = prefs.getEnum(KEY_SOURCES_ORDER, SourcesSortOrder.MANUAL)
		set(value) = prefs.putEnum(KEY_SOURCES_ORDER, value)

	var isSourcesGridMode: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_GRID, true)
		set(value) = prefs.putBoolean(KEY_SOURCES_GRID, value)

	var sourcesVersion: Int
		get() = prefs.getInt(KEY_SOURCES_VERSION, 0)
		set(value) = prefs.putInt(KEY_SOURCES_VERSION, value)

	var isAllSourcesEnabled: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_ENABLED_ALL, true)
		set(value) = prefs.putBoolean(KEY_SOURCES_ENABLED_ALL, value)

	val isPagesNumbersEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_NUMBERS, false)

	val screenshotsPolicy: ScreenshotsPolicy
		get() = prefs.getEnum(KEY_SCREENSHOTS_POLICY, ScreenshotsPolicy.ALLOW)

	val isAdBlockEnabled: Boolean
		get() = prefs.getBoolean(KEY_ADBLOCK, false)

	var userSpecifiedMangaDirectories: Set<String>
		get() {
			val set = prefs.getStringSet(KEY_LOCAL_MANGA_DIRS, emptySet()).orEmpty()
			return set
		}
		set(value) {
			val set = value
			prefs.putStringSet(KEY_LOCAL_MANGA_DIRS, set)
		}

	var mangaStorageDir: String?
		get() = prefs.getStringOrNull(KEY_LOCAL_STORAGE)
		set(value) {
			if (value == null) {
				prefs.remove(KEY_LOCAL_STORAGE)
			} else {
				val userDirs = userSpecifiedMangaDirectories
				if (value !in userDirs) {
					userSpecifiedMangaDirectories = userDirs + value
				}
				prefs.putString(KEY_LOCAL_STORAGE, value)
			}
		}

	var allowDownloadOnMeteredNetwork: TriStateOption
		get() = prefs.getEnum(KEY_DOWNLOADS_METERED_NETWORK, TriStateOption.ASK)
		set(value) = prefs.putEnum(KEY_DOWNLOADS_METERED_NETWORK, value)

	var preferredDownloadFormat: DownloadFormat
		get() = prefs.getEnum(KEY_DOWNLOADS_FORMAT, DownloadFormat.AUTOMATIC)
		set(value) = prefs.putEnum(KEY_DOWNLOADS_FORMAT, value)

	var isSuggestionsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS, false)
		set(value) = prefs.putBoolean(KEY_SUGGESTIONS, value)

	val isSuggestionsWiFiOnly: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_WIFI_ONLY, false)

	val isSuggestionsExcludeNsfw: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_EXCLUDE_NSFW, false)

	val isSuggestionsIncludeDisabledSources: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_DISABLED_SOURCES, false)

	val isSuggestionsNotificationAvailable: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_NOTIFICATIONS, false)

	val suggestionsTagsBlacklist: Set<String>
		get() {
			val string = prefs.getStringOrNull(KEY_SUGGESTIONS_EXCLUDE_TAGS)?.trimEnd(' ', ',')
			if (string.isNullOrEmpty()) {
				return emptySet()
			}
			return string.split(',').mapToSet { it.trim() }
		}

	val isReaderBarEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR, false)

	val isReaderBarTransparent: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR_TRANSPARENT, true)

	val isReaderChapterToastEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_CHAPTER_TOAST, true)

	val isReaderKeepScreenOn: Boolean
		get() = prefs.getBoolean(KEY_READER_SCREEN_ON, true)

	var imagesProxy: Int
		get() {
			val raw = prefs.getStringOrNull(KEY_IMAGES_PROXY)?.toIntOrNull()
			return raw ?: if (prefs.getBoolean(KEY_IMAGES_PROXY_OLD, false)) 0 else -1
		}
		set(value) = prefs.putString(KEY_IMAGES_PROXY, value.toString())

	var isSSLBypassEnabled: Boolean
		get() = prefs.getBoolean(KEY_SSL_BYPASS, false)
		set(value) = prefs.putBoolean(KEY_SSL_BYPASS, value)

	val proxyType: Proxy.Type
		get() {
			val raw = prefs.getStringOrNull(KEY_PROXY_TYPE) ?: return Proxy.Type.DIRECT
			return enumValues<Proxy.Type>().find { it.name == raw } ?: Proxy.Type.DIRECT
		}

	val proxyAddress: String?
		get() = prefs.getStringOrNull(KEY_PROXY_ADDRESS)

	val proxyPort: Int
		get() = prefs.getStringOrNull(KEY_PROXY_PORT)?.toIntOrNull() ?: 0

	val proxyLogin: String?
		get() = prefs.getStringOrNull(KEY_PROXY_LOGIN)?.nullIfEmpty()

	val proxyPassword: String?
		get() = prefs.getStringOrNull(KEY_PROXY_PASSWORD)?.nullIfEmpty()

	var localListOrder: SortOrder
		get() = prefs.getEnum(KEY_LOCAL_LIST_ORDER, SortOrder.NEWEST)
		set(value) = prefs.putEnum(KEY_LOCAL_LIST_ORDER, value)

	var historySortOrder: ListSortOrder
		get() = prefs.getEnum(KEY_HISTORY_ORDER, ListSortOrder.LAST_READ)
		set(value) = prefs.putEnum(KEY_HISTORY_ORDER, value)

	var allFavoritesSortOrder: ListSortOrder
		get() = prefs.getEnum(KEY_FAVORITES_ORDER, ListSortOrder.NEWEST)
		set(value) = prefs.putEnum(KEY_FAVORITES_ORDER, value)

	val isRelatedMangaEnabled: Boolean
		get() = prefs.getBoolean(KEY_RELATED_MANGA, true)

	val isWebtoonZoomEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_ZOOM, true)

	var isWebtoonGapsEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_GAPS, false)
		set(value) = prefs.putBoolean(KEY_WEBTOON_GAPS, value)

	var isWebtoonPullGestureEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_PULL_GESTURE, false)
		set(value) = prefs.putBoolean(KEY_WEBTOON_PULL_GESTURE, value)

	
	val defaultWebtoonZoomOut: Float
		get() = prefs.getInt(KEY_WEBTOON_ZOOM_OUT, 0).coerceIn(0, 50) / 100f

	
	var readerAutoscrollSpeed: Float
		get() = prefs.getFloat(KEY_READER_AUTOSCROLL_SPEED, 0f)
		set( value) = prefs.putFloat(
				KEY_READER_AUTOSCROLL_SPEED, value,
			)

	var isReaderAutoscrollFabVisible: Boolean
		get() = prefs.getBoolean(KEY_READER_AUTOSCROLL_FAB, true)
		set(value) = prefs.putBoolean(KEY_READER_AUTOSCROLL_FAB, value)

	val isPagesPreloadEnabled: Boolean
		get() {
			if (false /*isBackgroundNetworkRestricted*/) {
				return false
			}
			val policy = NetworkPolicy.from(
				prefs.getStringOrNull(KEY_PAGES_PRELOAD),
				NetworkPolicy.NON_METERED,
			)
			return policy.isNetworkAllowed()
		}

	var is32BitColorsEnabled: Boolean
		get() = prefs.getBoolean(KEY_32BIT_COLOR, false)
		set(value) = prefs.putBoolean(KEY_32BIT_COLOR, value)

	val isDiscordRpcEnabled: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC, false)

	val isDiscordRpcSkipNsfw: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC_SKIP_NSFW, false)

	var discordToken: String?
		get() = prefs.getStringOrNull(KEY_DISCORD_TOKEN)?.trim()?.nullIfEmpty()
		set(value) = if (value != null) prefs.putString(KEY_DISCORD_TOKEN, value.nullIfEmpty() ?: "") else prefs.remove(KEY_DISCORD_TOKEN)

	val isPeriodicalBackupEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_PERIODICAL_ENABLED, false)

    val periodicalBackupFrequency: Float
        get() = prefs.getStringOrNull(KEY_BACKUP_PERIODICAL_FREQUENCY)?.toFloatOrNull() ?: 7f

	val periodicalBackupFrequencyMillis: Long
        get() = (TimeUnit.DAYS.toMillis(1) * periodicalBackupFrequency).toLong()

	val periodicalBackupMaxCount: Int
		get() = if (prefs.getBoolean(KEY_BACKUP_PERIODICAL_TRIM, true)) {
			prefs.getInt(KEY_BACKUP_PERIODICAL_COUNT, 10)
		} else {
			Int.MAX_VALUE
		}

	var periodicalBackupDirectory: String?
		get() = prefs.getStringOrNull(KEY_BACKUP_PERIODICAL_OUTPUT)
		set(value) = if (value != null) prefs.putString(KEY_BACKUP_PERIODICAL_OUTPUT, value) else prefs.remove(KEY_BACKUP_PERIODICAL_OUTPUT)

	val isBackupTelegramUploadEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_TG_ENABLED, false)

	val backupTelegramChatId: String?
		get() = prefs.getStringOrNull(KEY_BACKUP_TG_CHAT)?.nullIfEmpty()

	val isReadingTimeEstimationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READING_TIME, true)

	val isPagesSavingAskEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_SAVE_ASK, true)

	val isStatsEnabled: Boolean
		get() = prefs.getBoolean(KEY_STATS_ENABLED, false)

	val isAutoLocalChaptersCleanupEnabled: Boolean
		get() = prefs.getBoolean(KEY_CHAPTERS_CLEAR_AUTO, false)

	fun isPagesCropEnabled(mode: ReaderMode): Boolean {
		val rawValue = prefs.getStringSet(KEY_READER_CROP, emptySet())
		if (rawValue.isNullOrEmpty()) {
			return false
		}
		val needle = if (mode == ReaderMode.WEBTOON) READER_CROP_WEBTOON else READER_CROP_PAGED
		return needle.toString() in rawValue
	}

	/** Toggle "Crop pages" for the bucket the [mode] belongs to (webtoon vs paged), Doki's multi-set. */
	fun setPagesCropEnabled(mode: ReaderMode, enabled: Boolean) {
		val needle = (if (mode == ReaderMode.WEBTOON) READER_CROP_WEBTOON else READER_CROP_PAGED).toString()
		val current = prefs.getStringSet(KEY_READER_CROP, emptySet()).toMutableSet()
		if (enabled) current.add(needle) else current.remove(needle)
		prefs.putStringSet(KEY_READER_CROP, current)
	}

	fun isTipEnabled(tip: String): Boolean {
		return !prefs.getStringSet(KEY_TIPS_CLOSED, emptySet()).contains(tip)
	}

	fun closeTip(tip: String) {
		val closedTips = prefs.getStringSet(KEY_TIPS_CLOSED, emptySet())
		if (tip in closedTips) {
			return
		}
		prefs.putStringSet(KEY_TIPS_CLOSED, closedTips + tip)
	}

	fun isIncognitoModeEnabled(isNsfw: Boolean): Boolean {
		return isIncognitoModeEnabled || (isNsfw && incognitoModeForNsfw == TriStateOption.ENABLED)
	}

	// Installed runtime-extension bundle version (null = using bundled baseline). Set by ExtensionManager.
	var installedExtensionVersion: String?
		get() = prefs.getStringOrNull(KEY_INSTALLED_EXT_VERSION)
		set(value) {
			if (value != null) prefs.putString(KEY_INSTALLED_EXT_VERSION, value) else prefs.remove(KEY_INSTALLED_EXT_VERSION)
		}

	fun getPagesSaveDirUri(): String? = prefs.getStringOrNull(KEY_PAGES_SAVE_DIR)

	fun setPagesSaveDir(uri: String?) {
		if (uri != null) {
			prefs.putString(KEY_PAGES_SAVE_DIR, uri)
		} else {
			prefs.remove(KEY_PAGES_SAVE_DIR)
		}
	}

	fun getMangaListBadges(): Int {
		val raw = prefs.getStringSet(KEY_MANGA_LIST_BADGES, mangaListBadgesDefault)
		var result = 0
		for (item in raw) {
			// Defensive: a malformed value (e.g. a cross-app backup that stored this as a JSON array) must
			// not crash the whole list screen — skip items that aren't plain integers.
			result = result or (item.trim().toIntOrNull() ?: continue)
		}
		return result
	}





	companion object {

		const val TRACK_HISTORY = "history"
		const val TRACK_FAVOURITES = "favourites"

		const val KEY_ADBLOCK = "adblock"
		const val KEY_LIST_MODE = "list_mode_2"
		const val KEY_LIST_MODE_HISTORY = "list_mode_history"
		const val KEY_LIST_MODE_FAVORITES = "list_mode_favorites"
		const val KEY_LIST_MODE_SUGGESTIONS = "list_mode_suggestions"
		const val KEY_THEME = "theme"
		const val KEY_COLOR_THEME = "color_theme"
		const val KEY_THEME_AMOLED = "amoled_theme"
		const val KEY_OFFLINE_DISABLED = "no_offline"
		const val KEY_PAGES_CACHE_CLEAR = "pages_cache_clear"
		const val KEY_HTTP_CACHE_CLEAR = "http_cache_clear"
		const val KEY_COOKIES_CLEAR = "cookies_clear"
		const val KEY_CHAPTERS_CLEAR = "chapters_clear"
		const val KEY_CHAPTERS_CLEAR_AUTO = "chapters_clear_auto"
		const val KEY_THUMBS_CACHE_CLEAR = "thumbs_cache_clear"
		const val KEY_SEARCH_HISTORY_CLEAR = "search_history_clear"
		const val KEY_UPDATES_FEED_CLEAR = "updates_feed_clear"
		const val KEY_GRID_SIZE = "grid_size"
		const val KEY_GRID_SIZE_PAGES = "grid_size_pages"
		const val KEY_REMOTE_SOURCES = "remote_sources"
		const val KEY_LOCAL_STORAGE = "local_storage"
		const val KEY_READER_DOUBLE_PAGES = "reader_double_pages"
		const val KEY_READER_DOUBLE_PAGES_SENSITIVITY = "reader_double_pages_sensitivity_2"
        const val KEY_READER_DOUBLE_FOLDABLE = "reader_double_foldable"
		const val KEY_READER_ZOOM_BUTTONS = "reader_zoom_buttons"
		const val KEY_READER_CONTROL_LTR = "reader_taps_ltr"
		const val KEY_READER_NAVIGATION_INVERTED = "reader_navigation_inverted"
		const val KEY_READER_FULLSCREEN = "reader_fullscreen"
		const val KEY_READER_VOLUME_BUTTONS = "reader_volume_buttons"
		const val KEY_READER_ORIENTATION = "reader_orientation"
		const val KEY_TRACKER_ENABLED = "tracker_enabled"
		const val KEY_AUTOFIX_ENABLED = "auto_fix_broken"
		const val KEY_TRACKER_WIFI_ONLY = "tracker_wifi"
		const val KEY_TRACKER_FREQUENCY = "tracker_freq"
		const val KEY_TRACK_SOURCES = "track_sources"
		const val KEY_TRACK_CATEGORIES = "track_categories"
		const val KEY_TRACK_WARNING = "track_warning"
		const val KEY_TRACKER_NOTIFICATIONS = "tracker_notifications"
		const val KEY_TRACKER_NO_NSFW = "tracker_no_nsfw"
		const val KEY_TRACKER_DOWNLOAD = "tracker_download"
		const val KEY_NOTIFICATIONS_SETTINGS = "notifications_settings"
		const val KEY_NOTIFICATIONS_SOUND = "notifications_sound"
		const val KEY_NOTIFICATIONS_VIBRATE = "notifications_vibrate"
		const val KEY_NOTIFICATIONS_LIGHT = "notifications_light"
		const val KEY_NOTIFICATIONS_INFO = "tracker_notifications_info"
		const val KEY_READER_ANIMATION = "reader_animation2"
		const val KEY_READER_CONTROLS = "reader_controls"
		const val KEY_READER_MODE = "reader_mode"
		const val KEY_READER_MODE_DETECT = "reader_mode_detect"
		const val KEY_READER_CROP = "reader_crop"
		const val KEY_APP_PASSWORD = "app_password"
		const val KEY_APP_PASSWORD_NUMERIC = "app_password_num"
		const val KEY_PROTECT_APP = "protect_app"
		const val KEY_PROTECT_APP_BIOMETRIC = "protect_app_bio"
		const val KEY_ZOOM_MODE = "zoom_mode"
		const val KEY_BACKUP = "backup"
		const val KEY_RESTORE = "restore"
		const val KEY_BACKUP_PERIODICAL_ENABLED = "backup_periodic"
		const val KEY_BACKUP_PERIODICAL_FREQUENCY = "backup_periodic_freq"
		const val KEY_BACKUP_PERIODICAL_TRIM = "backup_periodic_trim"
		const val KEY_BACKUP_PERIODICAL_COUNT = "backup_periodic_count"
		const val KEY_BACKUP_PERIODICAL_OUTPUT = "backup_periodic_output"
		const val KEY_BACKUP_PERIODICAL_LAST = "backup_periodic_last"
		const val KEY_HISTORY_GROUPING = "history_grouping"
		const val KEY_UPDATED_GROUPING = "updated_grouping"
		const val KEY_PROGRESS_INDICATORS = "progress_indicators"
		const val KEY_REVERSE_CHAPTERS = "reverse_chapters"
		const val KEY_GRID_VIEW_CHAPTERS = "grid_view_chapters"
		const val KEY_INCOGNITO_NSFW = "incognito_nsfw"
		const val KEY_PAGES_NUMBERS = "pages_numbers"
		const val KEY_SCREENSHOTS_POLICY = "screenshots_policy"
		const val KEY_PAGES_PRELOAD = "pages_preload"
		const val KEY_SUGGESTIONS = "suggestions"
		const val KEY_SUGGESTIONS_WIFI_ONLY = "suggestions_wifi"
		const val KEY_SUGGESTIONS_EXCLUDE_NSFW = "suggestions_exclude_nsfw"
		const val KEY_SUGGESTIONS_EXCLUDE_TAGS = "suggestions_exclude_tags"
		const val KEY_SUGGESTIONS_DISABLED_SOURCES = "suggestions_disabled_sources"
		const val KEY_SUGGESTIONS_NOTIFICATIONS = "suggestions_notifications"
		const val KEY_SHIKIMORI = "shikimori"
		const val KEY_ANILIST = "anilist"
		const val KEY_MAL = "mal"
		const val KEY_KITSU = "kitsu"
		const val KEY_DOWNLOADS_METERED_NETWORK = "downloads_metered_network"
		const val KEY_DOWNLOADS_FORMAT = "downloads_format"
		const val KEY_ALL_FAVOURITES_VISIBLE = "all_favourites_visible"
		const val KEY_DOH = "doh"
		const val KEY_EXIT_CONFIRM = "exit_confirm"
		const val KEY_INCOGNITO_MODE = "incognito"
		const val KEY_READER_MULTITASK = "reader_multitask"
		const val KEY_SYNC = "sync"
		const val KEY_SYNC_SETTINGS = "sync_settings"
		const val KEY_READER_BAR = "reader_bar"
		const val KEY_READER_BAR_TRANSPARENT = "reader_bar_transparent"
		const val KEY_READER_CHAPTER_TOAST = "reader_chapter_toast"
		const val KEY_READER_BACKGROUND = "reader_background"
		const val KEY_READER_SCREEN_ON = "reader_screen_on"
		const val KEY_SHORTCUTS = "dynamic_shortcuts"
		const val KEY_READER_TAP_ACTIONS = "reader_tap_actions"
		const val KEY_READER_OPTIMIZE = "reader_optimize"
		const val KEY_LOCAL_LIST_ORDER = "local_order"
		const val KEY_HISTORY_ORDER = "history_order"
		const val KEY_FAVORITES_ORDER = "fav_order"
		const val KEY_WEBTOON_GAPS = "webtoon_gaps"
		const val KEY_WEBTOON_ZOOM = "webtoon_zoom"
		const val KEY_WEBTOON_ZOOM_OUT = "webtoon_zoom_out"
		const val KEY_WEBTOON_PULL_GESTURE = "webtoon_pull_gesture"
		const val KEY_PREFETCH_CONTENT = "prefetch_content"
		const val KEY_APP_LOCALE = "app_locale"
		const val KEY_SOURCES_GRID = "sources_grid"
		const val KEY_UPDATES_UNSTABLE = "updates_unstable"
		const val KEY_TIPS_CLOSED = "tips_closed"
		const val KEY_SSL_BYPASS = "ssl_bypass"
		const val KEY_READER_AUTOSCROLL_SPEED = "as_speed"
		const val KEY_READER_AUTOSCROLL_FAB = "as_fab"
		const val KEY_MIRROR_SWITCHING = "mirror_switching"
		const val KEY_PROXY = "proxy"
		const val KEY_PROXY_TYPE = "proxy_type_2"
		const val KEY_PROXY_ADDRESS = "proxy_address"
		const val KEY_PROXY_PORT = "proxy_port"
		const val KEY_PROXY_AUTH = "proxy_auth"
		const val KEY_PROXY_LOGIN = "proxy_login"
		const val KEY_PROXY_PASSWORD = "proxy_password"
		const val KEY_IMAGES_PROXY = "images_proxy_2"
		const val KEY_LOCAL_MANGA_DIRS = "local_manga_dirs"
		const val KEY_DISABLE_NSFW = "no_nsfw"
		const val KEY_RELATED_MANGA = "related_manga"
		const val KEY_NAV_MAIN = "nav_main"
		val NAV_ITEMS_DEFAULT = listOf(NavItem.HISTORY, NavItem.FAVORITES, NavItem.EXPLORE, NavItem.FEED, NavItem.LOCAL)
		const val KEY_NAV_LABELS = "nav_labels"
		const val KEY_INSTALLED_EXT_VERSION = "installed_ext_version"
		const val KEY_NAV_PINNED = "nav_pinned"
		const val KEY_MAIN_FAB = "main_fab"
		const val KEY_32BIT_COLOR = "enhanced_colors"
		const val KEY_SOURCES_ORDER = "sources_sort_order"
		const val KEY_SOURCES_CATALOG = "sources_catalog"
		const val KEY_CF_BRIGHTNESS = "cf_brightness"
		const val KEY_CF_CONTRAST = "cf_contrast"
		const val KEY_CF_INVERTED = "cf_inverted"
		const val KEY_CF_GRAYSCALE = "cf_grayscale"
		const val KEY_CF_BOOK = "cf_book"
		const val KEY_PAGES_TAB = "pages_tab"
		const val KEY_DETAILS_TAB = "details_tab"
		const val KEY_DETAILS_LAST_TAB = "details_last_tab"
		const val KEY_READING_TIME = "reading_time"
		const val KEY_PAGES_SAVE_DIR = "pages_dir"
		const val KEY_PAGES_SAVE_ASK = "pages_dir_ask"
		const val KEY_STATS_ENABLED = "stats_on"
		const val KEY_FEED_HEADER = "feed_header"
		const val KEY_SEARCH_SUGGESTION_TYPES = "search_suggest_types"
		const val KEY_SOURCES_VERSION = "sources_version"
		const val KEY_SOURCES_ENABLED_ALL = "sources_enabled_all"
		const val KEY_QUICK_FILTER = "quick_filter"
		const val KEY_COLLAPSE_DESCRIPTION = "description_collapse"
		const val KEY_BACKUP_TG_ENABLED = "backup_periodic_tg_enabled"
		const val KEY_BACKUP_TG_CHAT = "backup_periodic_tg_chat_id"
		const val KEY_MANGA_LIST_BADGES = "manga_list_badges"
		const val KEY_TAGS_WARNINGS = "tags_warnings"
		const val KEY_DISCORD_RPC = "discord_rpc"
		const val KEY_DISCORD_RPC_SKIP_NSFW = "discord_rpc_skip_nsfw"
		const val KEY_DISCORD_TOKEN = "discord_token"

		// keys for non-persistent preferences
		const val KEY_APP_VERSION = "app_version"
		const val KEY_IGNORE_DOZE = "ignore_dose"
		const val KEY_TRACKER_DEBUG = "tracker_debug"
		const val KEY_LINK_WEBLATE = "about_app_translation"
		const val KEY_LINK_TELEGRAM = "about_telegram"
		const val KEY_LINK_GITHUB = "about_github"
		const val KEY_LINK_MANUAL = "about_help"
		const val KEY_PROXY_TEST = "proxy_test"
		const val KEY_OPEN_BROWSER = "open_browser"
		const val KEY_HANDLE_LINKS = "handle_links"
		const val KEY_BACKUP_TG = "backup_periodic_tg"
		const val KEY_BACKUP_TG_OPEN = "backup_periodic_tg_open"
		const val KEY_BACKUP_TG_TEST = "backup_periodic_tg_test"
		const val KEY_CLEAR_MANGA_DATA = "manga_data_clear"
		const val KEY_STORAGE_USAGE = "storage_usage"
		const val KEY_WEBVIEW_CLEAR = "webview_clear"

		// old keys are for migration only
		private const val KEY_IMAGES_PROXY_OLD = "images_proxy"

		// values
		private const val READER_CROP_PAGED = 1
		private const val READER_CROP_WEBTOON = 2
	}
}





