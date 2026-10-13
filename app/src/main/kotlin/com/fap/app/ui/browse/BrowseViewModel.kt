package com.fap.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fap.app.di.Graph
import com.fap.app.di.ViewerItem
import com.fap.app.repo.Repository
import com.fap.core.BrowseMode
import com.fap.core.CreatorRef
import com.fap.core.FapelloClient
import com.fap.core.FeedKind
import com.fap.app.di.toViewerItem
import com.fap.core.UserPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public sealed interface BrowseNav {
    public data class Creator(val slug: String) : BrowseNav
    public data class Post(val id: String, val slug: String) : BrowseNav
    public data object Viewer : BrowseNav
}

public data class BrowseUiState(
    val mode: BrowseMode = BrowseMode.CREATORS,
    val feedKind: FeedKind = FeedKind.NEW,
    val creators: List<CreatorRef> = emptyList(),
    val posts: List<UserPost> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val opening: Boolean = false,
    val toast: String? = null,
    /** Followed creators (the `favorites` table), for the Following chip. */
    val favoritesCount: Int = 0,
    // ---- site search (/search_v2 ajax): what the search box actually runs --
    /** True once a search has been submitted and its results are on screen. */
    val inSearch: Boolean = false,
    /** The query that produced the current search results. */
    val searchedQuery: String = "",
    val searchCreators: List<CreatorRef> = emptyList(),
    val searchPosts: List<UserPost> = emptyList(),
    val searchTotal: Int = 0,
    val searchHasMore: Boolean = false,
    val searchLoading: Boolean = false,
    val searchError: String? = null
)

public class BrowseViewModel : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    public val state: StateFlow<BrowseUiState> get() = _state

    private val _nav = MutableSharedFlow<BrowseNav>(extraBufferCapacity = 4)
    public val nav: kotlinx.coroutines.flow.SharedFlow<BrowseNav> = _nav.asSharedFlow()

    init {
        viewModelScope.launch {
            Graph.settings.state.collect { settings ->
                val changed = settings.mode != _state.value.mode || settings.feedKind != _state.value.feedKind
                _state.update { it.copy(mode = settings.mode, feedKind = settings.feedKind) }
                if (changed || _state.value.page == 0) reload()
            }
        }
    }

    public fun reload() {
        _state.update {
            it.copy(page = 0, creators = emptyList(), posts = emptyList(), endReached = false, error = null)
        }
        loadMore()
    }

    public fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        val nextPage = current.page + 1
        viewModelScope.launch {
            _state.update { it.copy(loading = nextPage == 1, loadingMore = nextPage > 1, error = null) }
            try {
                when (current.mode) {
                    BrowseMode.CREATORS -> {
                        val result = Repository.feed(current.feedKind, nextPage)
                        _state.update {
                            it.copy(
                                creators = (it.creators + result.items).distinctBy { creator -> creator.slug },
                                page = nextPage,
                                endReached = !result.hasMore
                            )
                        }
                    }
                    BrowseMode.POSTS -> {
                        val result = Repository.userPosts(nextPage)
                        _state.update {
                            it.copy(
                                posts = (it.posts + result.items).distinctBy { post -> post.id },
                                page = nextPage,
                                endReached = !result.hasMore
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Request failed", endReached = true) }
            } finally {
                _state.update { it.copy(loading = false, loadingMore = false) }
            }
        }
    }

    /**
     * Re-read the followed-creators count. Called every time the Browse
     * screen comes back into view, so following/unfollowing a creator on
     * its own screen is reflected here without restarting the app.
     */
    public fun refreshFavorites() {
        viewModelScope.launch {
            val count = runCatching { Repository.favorites().size }.getOrDefault(_state.value.favoritesCount)
            _state.update { it.copy(favoritesCount = count) }
        }
    }

    public fun setQuery(value: String) {
        _state.update {
            // Clearing the box leaves search and goes back to the feed.
            if (value.isBlank() && it.inSearch) {
                it.copy(
                    query = value, inSearch = false, searchedQuery = "",
                    searchCreators = emptyList(), searchPosts = emptyList(),
                    searchTotal = 0, searchHasMore = false, searchError = null
                )
            } else {
                it.copy(query = value)
            }
        }
    }

    /**
     * What pressing Search does. Pasted links/full slugs still open directly;
     * anything else runs the site's real search (`/search_v2/?ajax=1`) for the
     * current mode instead of just filtering whatever feed pages happen to be
     * loaded — which is why searching used to find almost nothing.
     */
    public fun submitSearch() {
        val raw = _state.value.query.trim()
        if (raw.isEmpty()) return
        if (looksLikeLink(raw)) {
            openRaw(raw)
            return
        }
        runSearch(raw, offset = 0)
    }

    public fun loadMoreSearch() {
        val current = _state.value
        if (!current.inSearch || current.searchLoading || !current.searchHasMore) return
        val offset = when (current.mode) {
            BrowseMode.CREATORS -> current.searchCreators.size
            BrowseMode.POSTS -> current.searchPosts.size
        }
        runSearch(current.searchedQuery, offset = offset)
    }

    private fun runSearch(query: String, offset: Int) {
        val mode = _state.value.mode
        viewModelScope.launch {
            _state.update {
                it.copy(
                    inSearch = true,
                    searchedQuery = query,
                    searchLoading = true,
                    searchError = null,
                    searchCreators = if (offset == 0) emptyList() else it.searchCreators,
                    searchPosts = if (offset == 0) emptyList() else it.searchPosts
                )
            }
            try {
                when (mode) {
                    BrowseMode.CREATORS -> {
                        val result = Repository.searchCreators(query, offset)
                        _state.update {
                            it.copy(
                                searchCreators = (it.searchCreators + result.items).distinctBy { creator -> creator.slug },
                                searchTotal = result.total,
                                searchHasMore = result.hasMore
                            )
                        }
                    }
                    BrowseMode.POSTS -> {
                        val result = Repository.searchPosts(query, offset)
                        _state.update {
                            it.copy(
                                searchPosts = (it.searchPosts + result.items).distinctBy { post -> post.id },
                                searchTotal = result.total,
                                searchHasMore = result.hasMore
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(searchError = if (offset == 0) e.message ?: "Search failed" else it.searchError)
                }
            } finally {
                _state.update { it.copy(searchLoading = false) }
            }
        }
    }

    /** Back to browsing the feed. */
    public fun exitSearch() {
        _state.update {
            it.copy(
                inSearch = false, searchedQuery = "", query = "",
                searchCreators = emptyList(), searchPosts = emptyList(),
                searchTotal = 0, searchHasMore = false, searchError = null
            )
        }
    }

    private fun looksLikeLink(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.contains("fapello.com") || lower.contains("/post/") ||
            (lower.startsWith("/") && lower.length > 2)
    }

    public fun setMode(mode: BrowseMode) {
        // Search results are per-mode (models vs posts): switching modes
        // leaves search and reloads the feed (the settings collector does it).
        if (_state.value.inSearch) exitSearch()
        Graph.settings.update { it.copy(mode = mode) }
    }

    public fun setFeed(kind: FeedKind) {
        Graph.settings.update { it.copy(feedKind = kind) }
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    /** Download everything a creator has, page by page, in the background. */
    public fun downloadCreator(creator: CreatorRef) {
        Graph.downloads.enqueueCreator(creator.slug, creator.name)
        _state.update { it.copy(toast = "Queued a full crawl of ${creator.name}") }
    }

    public fun downloadPost(post: UserPost) {
        viewModelScope.launch {
            _state.update { it.copy(toast = "Fetching post…") }
            runCatching { Repository.userPostMedia(post) }
                .onSuccess { media ->
                    Graph.downloads.enqueue(media, post.id)
                    _state.update { it.copy(toast = "Queued ${media.size} files") }
                }
                .onFailure { error ->
                    _state.update { it.copy(toast = "Failed: ${error.message}") }
                }
        }
    }

    /** Search box doubles as a url / slug opener. */
    public fun openRaw(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(opening = true) }
            try {
                when (val opened = Repository.open(value)) {
                    is FapelloClient.Opened.Creator -> _nav.emit(BrowseNav.Creator(opened.profile.creator.slug))
                    is FapelloClient.Opened.Post -> {
                        Graph.viewer.set(
                            opened.media.map { it.toViewerItem() },
                            0,
                            opened.post.title
                        )
                        _state.update { it.copy(toast = "Opened post · ${opened.media.size} media") }
                        _nav.emit(BrowseNav.Viewer)
                    }
                    is FapelloClient.Opened.Media -> {
                        Graph.viewer.set(listOf(opened.item.toViewerItem()), 0, opened.item.fileName())
                        _nav.emit(BrowseNav.Viewer)
                    }
                    FapelloClient.Opened.Unknown -> _state.update { it.copy(toast = "Could not understand that link") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(toast = "Failed: ${e.message}") }
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }
}
