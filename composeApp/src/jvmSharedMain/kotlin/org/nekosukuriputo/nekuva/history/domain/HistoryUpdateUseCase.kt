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
	) = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch(Dispatchers.Default, CoroutineStart.ATOMIC) {
		runCatchingCancellable {
			withContext(NonCancellable) {
				invoke(manga, chapterId, page, scroll, percent)
			}
		}.onFailure { e ->
            println("HistoryUpdateUseCase Error: ${e.message}")
            e.printStackTrace()
        }
	}
}
