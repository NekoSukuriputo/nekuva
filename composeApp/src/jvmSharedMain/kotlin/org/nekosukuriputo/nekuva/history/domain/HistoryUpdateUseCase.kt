package org.nekosukuriputo.nekuva.history.domain

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

class HistoryUpdateUseCase(
	private val historyRepository: HistoryRepository,
) {

	// Single long-lived scope instead of allocating a new throwaway scope per page change.
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	suspend operator fun invoke(manga: Manga, chapterId: Long, page: Int, scroll: Int, percent: Float) {
		historyRepository.addOrUpdate(
			manga = manga,
			chapterId = chapterId,
			page = page,
			scroll = scroll,
			percent = percent,
			force = false,
		)
	}

	fun invokeAsync(
		manga: Manga,
		chapterId: Long,
		page: Int,
		scroll: Int,
		percent: Float
	) = scope.launch(start = CoroutineStart.ATOMIC) {
		runCatchingCancellable {
			withContext(NonCancellable) {
				invoke(manga, chapterId, page, scroll, percent)
			}
		}.onFailure { e ->
			// NEVER swallow silently — a failed write is exactly why History showed empty (§4.6).
			println("[Nekuva][history] addOrUpdate FAILED mangaId=${manga.id} chapter=$chapterId page=$page: ${e.message}")
			e.printStackTrace()
		}
	}
}
