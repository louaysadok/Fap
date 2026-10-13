package com.fap.app.ui.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fap.app.di.Graph
import com.fap.app.di.toViewerItem
import com.fap.app.repo.Repository
import com.fap.core.CreatorProfile
import com.fap.core.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class CreatorUiState(
    val slug: String = "",
    val profile: CreatorProfile? = null,
    val media: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val favorite: Boolean = false,
    val toast: String? = null
)

public class CreatorViewModel(private val slug: String) : ViewModel() {

    private val _state = MutableStateFlow(CreatorUiState(slug = slug))
    public val state: StateFlow<CreatorUiState> get() = _state

    private val _openViewer = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    public val openViewer = _openViewer.asSharedFlow()

    init {
        load()
    }

    public fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, page = 0, media = emptyList(), endReached = false) }
            try {
                val profile = Repository.creator(slug)
                val favorite = Graph.db.isFavorite(slug)
                _state.update {
                    it.copy(
                        profile = profile,
                        media = profile.media,
                        page = 1,
                        favorite = favorite,
                        endReached = profile.media.size < (profile.reportedTotal ?: profile.media.size + 1)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not load this creator") }
            } finally {
                _state.update { it.copy(loading = false) }
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
                val result = Repository.creatorMedia(slug, next)
                _state.update {
                    it.copy(
                        media = (it.media + result.items).distinctBy { item -> item.id },
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

    public fun toggleSelectionMode() {
        _state.update { it.copy(selectionMode = !it.selectionMode, selected = emptySet()) }
    }

    public fun toggleSelected(id: String) {
        _state.update { state ->
            val next = state.selected.toMutableSet().apply { if (!add(id)) remove(id) }
            state.copy(selected = next)
        }
    }

    public fun selectAll() {
        _state.update { it.copy(selected = it.media.map { item -> item.id }.toSet()) }
    }

    public fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    public fun downloadSelected() {
        val state = _state.value
        val items = state.media.filter { it.id in state.selected }
        if (items.isEmpty()) return
        Graph.downloads.enqueue(items, slug)
        _state.update { it.copy(selected = emptySet(), selectionMode = false, toast = "Queued ${items.size} file(s)") }
    }

    /** Everything, including pages we have not scrolled to yet. */
    public fun downloadAll() {
        val name = _state.value.profile?.creator?.name ?: slug
        Graph.downloads.enqueueCreator(slug, name)
        _state.update { it.copy(toast = "Crawling all pages of $name…") }
    }

    public fun toggleFavorite() {
        val state = _state.value
        val profile = state.profile ?: return
        val next = !state.favorite
        Graph.db.setFavorite(
            slug = profile.creator.slug,
            name = profile.creator.name,
            avatar = profile.creator.avatarUrl,
            mediaCount = profile.creator.mediaCount,
            favorite = next
        )
        _state.update { it.copy(favorite = next, toast = if (next) "Following ${profile.creator.name}" else "Unfollowed") }
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    public fun openAt(index: Int) {
        val state = _state.value
        if (state.media.isEmpty()) return
        Graph.viewer.set(state.media.map { it.toViewerItem() }, index, state.profile?.creator?.name ?: slug)
        _openViewer.tryEmit(index)
    }

    public companion object {
        public fun factory(slug: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CreatorViewModel(slug) as T
        }
    }
}
