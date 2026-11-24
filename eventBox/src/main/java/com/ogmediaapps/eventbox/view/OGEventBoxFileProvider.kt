package com.ogmediaapps.eventbox.view

import androidx.core.content.FileProvider

/**
 * Event Box's own [FileProvider] subclass, so its manifest entry can't collide with a host app
 * that also declares `androidx.core.content.FileProvider`.
 */
internal class OGEventBoxFileProvider : FileProvider()
