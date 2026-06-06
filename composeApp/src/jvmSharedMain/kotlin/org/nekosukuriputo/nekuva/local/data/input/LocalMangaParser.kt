package org.nekosukuriputo.nekuva.local.data.input

import org.nekosukuriputo.nekuva.core.util.ext.buildUpon
import org.nekosukuriputo.nekuva.core.util.ext.toFile
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.openZip
import org.jetbrains.annotations.Blocking
import org.nekosukuriputo.nekuva.core.model.LocalMangaSource
import org.nekosukuriputo.nekuva.core.util.AlphanumComparator
import org.nekosukuriputo.nekuva.core.util.MimeTypes
import org.nekosukuriputo.nekuva.core.util.ext.URI_SCHEME_ZIP
import org.nekosukuriputo.nekuva.core.util.ext.isDirectory
import org.nekosukuriputo.nekuva.core.util.ext.isFileUri
import org.nekosukuriputo.nekuva.core.util.ext.isImage
import org.nekosukuriputo.nekuva.core.util.ext.isRegularFile
import org.nekosukuriputo.nekuva.core.util.ext.isZipUri
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.core.util.ext.toFileNameSafe
import org.nekosukuriputo.nekuva.local.data.hasZipExtension
import org.nekosukuriputo.nekuva.local.data.isZipArchive
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.util.longHashCode
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.parsers.util.toTitleCase
import java.io.File

class LocalMangaParser(private val uri: URI) {

	constructor(file: File) : this(file.toURI())

	private val rootFile: File = File(uri.schemeSpecificPart)

	suspend fun getManga(withDetails: Boolean): LocalManga = runInterruptible(Dispatchers.IO) {
		uri.resolveFsAndPath().use { (fileSystem, rootPath) ->
			val title = rootFile.name.fileNameToTitle()
			val manga = Manga(
				id = rootFile.absolutePath.longHashCode(),
				title = title,
				url = rootFile.toURI().toString(),
				publicUrl = rootFile.toURI().toString(),
				source = LocalMangaSource,
				coverUrl = fileSystem.findFirstImageUri(rootPath)?.toString(),
				chapters = if (withDetails) {
					val chapters = fileSystem.listRecursively(rootPath)
						.mapNotNullTo(HashSet()) { path ->
							when {
								!fileSystem.isRegularFile(path) -> null
								path.isImage() -> path.parent
								hasZipExtension(path.name) -> path
								else -> null
							}
						}.sortedWith(compareBy(AlphanumComparator()) { x -> x.toString() })
					chapters.mapIndexed { i, p ->
						val s = if (p.root == rootPath.root) {
							p.relativeTo(rootPath).toString()
						} else {
							p.toString()
						}.removePrefix(Path.DIRECTORY_SEPARATOR)
						MangaChapter(
							id = "$i$s".longHashCode(),
							title = p.userFriendlyName(),
							number = 0f,
							volume = 0,
							source = LocalMangaSource,
							uploadDate = 0L,
							url = uri.child(p.relativeTo(rootPath), resolve = false).toString(),
							scanlator = null,
							branch = null,
						)
					}
				} else {
					null
				},
				altTitles = emptySet(),
				rating = -1f,
				contentRating = null,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = null,
				description = null,
			)
			LocalManga(manga, rootFile)
		}
	}

	suspend fun getMangaInfo(): Manga? = runInterruptible(Dispatchers.IO) {
		null
	}

	suspend fun getPages(chapter: MangaChapter): List<MangaPage> = runInterruptible(Dispatchers.IO) {
		val chapterUri = java.net.URI(chapter.url).resolve()
		chapterUri.resolveFsAndPath().use { (fileSystem, rootPath) ->
			val entries = fileSystem.listRecursively(rootPath).filter { fileSystem.isRegularFile(it) }
			entries.filter { x -> x.isImage() && x.parent == rootPath }
				.sortedWith(compareBy(AlphanumComparator()) { x -> x.toString() })
				.map { x ->
					val entryUri = chapterUri.child(x, resolve = true).toString()
					MangaPage(
						id = entryUri.longHashCode(),
						url = entryUri,
						preview = null,
						source = LocalMangaSource,
					)
				}.toList()
		}
	}

	private fun URI.child(path: Path, resolve: Boolean): URI {
		val file = fileFromPath()
		val builder = buildUpon()
		val isZip = isZipUri() || file.isZipArchive
		if (isZip) {
			builder.scheme(URI_SCHEME_ZIP)
		}
		if (isZip || !resolve) {
			builder.fragment(path.toString().removePrefix(Path.DIRECTORY_SEPARATOR))
		} else {
			builder.appendEncodedPath(path.relativeTo(file.toOkioPath()).toString())
		}
		return builder.build()
	}

	private fun FileSystem.findFirstImageUri(
		rootPath: Path,
		recursive: Boolean = false
	): URI? = runCatchingCancellable {
		val list = list(rootPath)
		for (file in list.sortedWith(compareBy(AlphanumComparator()) { x -> x.name })) {
			if (isRegularFile(file)) {
				if (file.isImage()) {
					return@runCatchingCancellable uri.child(file, resolve = true)
				}
				if (recursive && file.isZip()) {
					openZip(file).use { zipFs ->
						zipFs.findFirstImageUri(Path.DIRECTORY_SEPARATOR.toPath())?.let { subUri ->
							val subPath = subUri.path.orEmpty().removePrefix(uri.path.orEmpty())
								.replace(Regex("^(/\\.\\.)+"), "")
							return@runCatchingCancellable uri.child(file, resolve = true)
								.child(subPath.toPath(), resolve = false)
						}
					}
				}
			} else if (recursive && isDirectory(file)) {
				findFirstImageUri(file, true)?.let {
					return@runCatchingCancellable it
				}
			}
		}
		if (recursive) {
			null
		} else {
			findFirstImageUri(rootPath, recursive = true)
		}
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()

	private fun Path.userFriendlyName(): String = name.substringBeforeLast('.')
		.replace('_', ' ')
		.toTitleCase()

	private class FsAndPath(
		val fileSystem: FileSystem,
		val path: Path,
		private val isCloseable: Boolean,
	) : AutoCloseable {

		override fun close() {
			if (isCloseable) {
				fileSystem.close()
			}
		}

		operator fun component1() = fileSystem

		operator fun component2() = path
	}

	companion object {
		@Blocking
		fun getOrNull(file: File): LocalMangaParser? = if ((file.isDirectory || file.isZipArchive) && file.canRead()) {
			LocalMangaParser(file)
		} else {
			null
		}

		suspend fun find(roots: Iterable<File>, manga: Manga): LocalMangaParser? = channelFlow {
			val fileName = manga.title.toFileNameSafe()
			for (root in roots) {
				launch {
					val parser = getOrNull(File(root, fileName)) ?: getOrNull(File(root, "$fileName.cbz"))
					val info = runCatchingCancellable { parser?.getMangaInfo() }.getOrNull()
					if (info?.id == manga.id) {
						send(parser)
					}
				}
			}
		}.flowOn(Dispatchers.Default).firstOrNull()

		private fun Path.isImage(): Boolean = MimeTypes.getMimeTypeFromExtension(name)?.startsWith("image/") == true
		private fun Path.isZip(): Boolean = hasZipExtension(name)
		private fun URI.resolve(): URI = if (isFileUri()) {
			val file = toFile()
			if (file.isZipArchive) {
				this
			} else if (file.isDirectory) {
				file.resolve(fragment.orEmpty()).toURI()
			} else {
				this
			}
		} else {
			this
		}

		private fun URI.fileFromPath(): File = File(requireNotNull(path) { "Uri path is null: $this" })

		@Blocking
		private fun URI.resolveFsAndPath(): FsAndPath {
			val resolved = resolve()
			return when {
				resolved.isZipUri() -> FsAndPath(
					FileSystem.SYSTEM.openZip(resolved.schemeSpecificPart.toPath()),
					resolved.fragment.orEmpty().toRootedPath(),
					isCloseable = true,
				)

				isFileUri() -> {
					val file = toFile()
					if (file.isZipArchive) {
						FsAndPath(
							FileSystem.SYSTEM.openZip(schemeSpecificPart.toPath()),
							fragment.orEmpty().toRootedPath(),
							isCloseable = true,
						)
					} else {
						FsAndPath(FileSystem.SYSTEM, file.toOkioPath(), isCloseable = false)
					}
				}

				else -> throw IllegalArgumentException("Unsupported uri $resolved")
			}
		}

		private fun String.toRootedPath(): Path = if (startsWith(Path.DIRECTORY_SEPARATOR)) {
			this
		} else {
			Path.DIRECTORY_SEPARATOR + this
		}.toPath()

		private fun String.fileNameToTitle() = substringBeforeLast('.')
			.replace('_', ' ')
			.replaceFirstChar { it.uppercase() }
	}
}

