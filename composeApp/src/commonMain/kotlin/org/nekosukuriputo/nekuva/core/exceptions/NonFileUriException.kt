package org.nekosukuriputo.nekuva.core.exceptions


class NonFileUriException(
	val uri: String,
) : IllegalArgumentException("Cannot resolve file name of \"$uri\"")

