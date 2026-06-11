package org.nekosukuriputo.nekuva.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BackupIndex(
    @SerialName("app_id") val appId: String = "org.nekosukuriputo.nekuva",
    @SerialName("app_version") val appVersion: Int = 1,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
)
