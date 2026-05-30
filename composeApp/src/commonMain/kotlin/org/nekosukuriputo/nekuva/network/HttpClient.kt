package org.nekosukuriputo.nekuva.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
