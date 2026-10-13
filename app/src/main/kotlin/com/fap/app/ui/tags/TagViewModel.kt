package com.fap.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fap.app.di.Graph
import com.fap.app.di.toViewerItem
import com.fap.app.repo.Repository
import com.fap.core.FapelloUrls
import com.fap.core.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class TagUiState(
    val slug: String = "",
    val name: String = "",
    val media: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val opening: Boolean = false,
    val toast: String? = null
)

/** One tag's media feed (`/ajax/tags/{slug}/page-N/`), creator-screen style. */
public class TagViewModel(private val slug: String) : ViewModel() {

    private val _state = MutableStateFlow(
        TagUiState(slug = slug, name = FapelloUrls.prettyName(slug))
    )
    public val state: StateFlow<TagUiState> get() = _state

    private val _openViewer = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    public val openViewer = _openViewer.asSharedFlow()

    init {
        load()
    }

    public fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, page = 0, media = emptyList(), endReached = false) }
            try {
                val result = Repository.tagMedia(slug, 1)
                _state.update {
                    it.copy(media = result.items, page = 1, endReached = !result.hasMore, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load this tag") }
            }
        }
    }

    public fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        val next = current.page + 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val result = Repository.tagMedia(slug, next)
                _state.update {
                    it.copy(
                        media = (it.media + result.items).distinctBy { item -> item.pageUrl },
                        page = next,
                        endReached = !result.hasMore
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(endReached = true) }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * Tag cards don't say photo or video, so resolve the tapped item's own
     * page first (one request) to learn its real file/kind, then open the
     * viewer on the whole tag set.
     */
    public fun openAt(index: Int) {
        val current = _state.value
        if (current.media.isEmpty() || current.opening) return
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            val items = current.media.toMutableList()
            runCatching { Repository.resolve(items[index]) }
                .onSuccess { resolved -> items[index] = resolved }
            Graph.viewer.set(items.map { it.toViewerItem() }, index, "#${current.name}")
            _state.update { it.copy(opening = false) }
            _openViewer.tryEmit(index)
        }
    }

    public fun downloadAll() {
        val current = _state.value
        Graph.downloads.enqueueTag(current.slug, current.name)
        _state.update { it.copy(toast = "Crawling all pages of #${current.name}…") }
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    public companion object {
        public fun factory(slug: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TagViewModel(slug) as T
        }
    }
}
