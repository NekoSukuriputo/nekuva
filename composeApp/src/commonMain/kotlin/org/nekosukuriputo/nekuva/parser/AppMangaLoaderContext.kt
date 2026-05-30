package org.nekosukuriputo.nekuva.parser

expect class AppMangaLoaderContext() {
    suspend fun fetchDummyData(): String
}
