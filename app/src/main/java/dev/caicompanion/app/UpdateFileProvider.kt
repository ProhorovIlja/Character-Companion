package dev.caicompanion.app

import androidx.core.content.FileProvider

/** Dedicated private provider; the manifest exposes only the verified update cache path. */
class UpdateFileProvider : FileProvider()
