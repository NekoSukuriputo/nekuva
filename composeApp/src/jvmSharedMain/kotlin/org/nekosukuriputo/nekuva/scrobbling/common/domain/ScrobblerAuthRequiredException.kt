package org.nekosukuriputo.nekuva.scrobbling.common.domain

import okio.IOException
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService

/** Thrown when a scrobbler API call fails because the saved token is missing/expired → re-login. */
class ScrobblerAuthRequiredException(
    val scrobblerService: ScrobblerService,
) : IOException("Authorization required for ${scrobblerService.name}")
