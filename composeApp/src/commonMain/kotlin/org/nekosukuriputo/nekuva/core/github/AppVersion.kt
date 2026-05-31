package org.nekosukuriputo.nekuva.core.github


data class AppVersion(
	val id: Long,
	val name: String,
	val url: String,
	val apkSize: Long,
	val apkUrl: String,
	val description: String,
){

	val versionId = VersionId(name)
}


