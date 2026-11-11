package com.paisatrail.data.share

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SharedItem(val uri: Uri, val isPdf: Boolean)

/**
 * Hand-off point between whatever received a file (the share intent in MainActivity, or the
 * in-app pickers) and the screen that will process it. The screen's ViewModel takes the item
 * exactly once.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<SharedItem?>(null)
    val pending: StateFlow<SharedItem?> = _pending

    fun post(item: SharedItem) {
        _pending.value = item
    }

    /** Returns and clears the pending item if it matches the wanted kind. */
    fun take(wantPdf: Boolean): SharedItem? {
        val item = _pending.value ?: return null
        if (item.isPdf != wantPdf) return null
        _pending.value = null
        return item
    }
}
