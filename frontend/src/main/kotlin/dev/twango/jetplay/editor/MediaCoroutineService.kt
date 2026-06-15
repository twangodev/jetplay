package dev.twango.jetplay.editor

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

// Platform-injected, project-lifecycle CoroutineScope; each MediaLoader takes a childScope of it.
@Service(Service.Level.PROJECT)
class MediaCoroutineService(val scope: CoroutineScope)
