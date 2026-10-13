package com.fap.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fap.app.di.Graph
import com.fap.app.di.toViewerItem
import com.fap.app.repo.Repository
import com.fap.core.FapelloUrls
import com.fap.core.MediaItem
import com.fap.core.UserPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class PostUiState(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val media: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null
)

public class PostViewModel(private val id: String, private val slug: String) : ViewModel() {

    private val post: UserPost = UserPost(
        id = id,
        slug = slug,
        title = FapelloUrls.prettyName(slug),
        url = FapelloUrls.post(Graph.client().base, id, slug)
    )

    private val _state = MutableStateFlow(PostUiState(id = id, slug = slug, title = post.title))
    public val state: StateFlow<PostUiState> get() = _state

    private val _openViewer = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    public val openViewer = _openViewer.asSharedFlow()

    init {
        load()
    }

    public fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val media = Repository.userPostMedia(post)
                _state.update { it.copy(media = media, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load this post") }
            }
        }
    }

    public fun downloadAll() {
        val media = _state.value.media
        if (media.isEmpty()) return
        Graph.downloads.enqueue(media, id)
        _state.update { it.copy(toast = "Queued ${media.size} file(s)") }
    }

    public fun openAt(index: Int) {
        val media = _state.value.media
        if (media.isEmpty()) return
        Graph.viewer.set(media.map { it.toViewerItem() }, index, _state.value.title)
        _openViewer.tryEmit(index)
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    public companion object {
        public fun factory(id: String, slug: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PostViewModel(id, slug) as T
            }
    }
}
