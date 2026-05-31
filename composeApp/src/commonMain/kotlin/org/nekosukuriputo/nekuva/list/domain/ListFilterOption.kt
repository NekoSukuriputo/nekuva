package org.nekosukuriputo.nekuva.list.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.nekosukuriputo.nekuva.core.db.entity.toEntity
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.core.model.LocalMangaSource
import org.nekosukuriputo.nekuva.core.model.unwrap
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.MangaTag

sealed interface ListFilterOption {

	@get:StringRes
	val titleResId: Int

	@get:DrawableRes
	val iconResId: Int

	val titleText: CharSequence?

	val groupKey: String

	fun getIconData(): Any? = null

	data object Downloaded : ListFilterOption {

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = 0

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_downloaded"
	}

	enum class Macro(
		@StringRes override val titleResId: Int,
		@DrawableRes override val iconResId: Int,
	) : ListFilterOption {

		COMPLETED(0, 0),
		NEW_CHAPTERS(0, 0),
		FAVORITE(0, 0),
		NSFW(0, 0),
		;

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = name
	}

	data class Branch(
		override val titleText: String?,
		val chaptersCount: Int,
	) : ListFilterOption {

		override val titleResId: Int
			get() = if (titleText == null) 0 else 0

		override val iconResId: Int
			get() = 0

		override val groupKey: String
			get() = "_branch"
	}

	data class Tag(
		val tag: MangaTag
	) : ListFilterOption {

		val tagId: Long = tag.toEntity().id

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = 0

		override val titleText: String
			get() = tag.title

		override val groupKey: String
			get() = "_tag"
	}

	data class Favorite(
		val category: FavouriteCategory
	) : ListFilterOption {

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = 0

		override val titleText: String
			get() = category.title

		override val groupKey: String
			get() = "_favcat"
	}

	data class Source(
		val mangaSource: MangaSource
	) : ListFilterOption {
		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = 0

		override val titleText: CharSequence?
			get() = when (val source = mangaSource.unwrap()) {
				is MangaParserSource -> source.title
				else -> null
			}

		override val groupKey: String
			get() = "_source"

		override fun getIconData(): Any? = null
	}

	data class Inverted(
		val option: ListFilterOption,
		override val iconResId: Int,
		override val titleResId: Int,
		override val titleText: CharSequence?,
	) : ListFilterOption {

		override val groupKey: String
			get() = "_inv" + option.groupKey
	}

	companion object {

		val SFW
			get() = Inverted(
				option = Macro.NSFW,
				iconResId = 0,
				titleResId = 0,
				titleText = null,
			)

		val NOT_FAVORITE
			get() = Inverted(
				option = Macro.FAVORITE,
				iconResId = 0,
				titleResId = 0,
				titleText = null,
			)
	}
}

