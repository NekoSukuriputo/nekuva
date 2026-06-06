package org.nekosukuriputo.nekuva.local.domain

import org.nekosukuriputo.nekuva.core.util.MultiMutex
import org.nekosukuriputo.nekuva.parsers.model.Manga



class MangaLock constructor() : MultiMutex<Manga>()


