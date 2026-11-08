package dev.twango.jetplay.media

/** Uses the original VFS path, including in Remote Dev; never a temporary playback file or URL. */
internal fun mediaReferencePath(filePath: String, projectBasePath: String?): String {
    if (projectBasePath.isNullOrBlank()) return filePath
    val prefix = projectBasePath.replace('\\', '/').trimEnd('/') + "/"
    val normalized = filePath.replace('\\', '/')
    return if (normalized.startsWith(prefix)) normalized.removePrefix(prefix) else filePath
}
