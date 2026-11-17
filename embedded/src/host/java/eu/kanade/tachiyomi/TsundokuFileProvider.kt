package eu.kanade.tachiyomi

import androidx.core.content.FileProvider

/**
 * Tsundoku's FileProvider (authority `${applicationId}.provider`) when embedded in another app.
 *
 * The host app declares its own `androidx.core.content.FileProvider`, and a manifest can't declare
 * the same provider class twice, so the embedded manifest uses this subclass instead.
 * [FileProvider.getUriForFile] looks providers up by authority, so no calling code changes.
 */
class TsundokuFileProvider : FileProvider()
