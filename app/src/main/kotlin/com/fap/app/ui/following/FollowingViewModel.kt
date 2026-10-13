package com.fap.app.ui.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import com.fap.app.repo.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class FollowingUiState(
    val favorites: List<AppDatabase.Favorite> = emptyList(),
    val loading: Boolean = false,
    val toast: String? = null
)

/**
 * The followed creators, straight from the `favorites` table. [refresh] is
 * called on init and every time the screen comes back into view, so a
 * follow/unfollow made on a Creator screen shows up here (and the Browse
 * chip count, which re-reads the same table, stays in step).
 */
public class FollowingViewModel : ViewModel() {

    private val _state = MutableStateFlow(FollowingUiState())
    public val state: StateFlow<FollowingUiState> get() = _state

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val favorites = Repository.favorites()
                _state.update { it.copy(favorites = favorites, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, toast = e.message ?: "Could not load following") }
            }
        }
    }

    public fun unfollow(favorite: AppDatabase.Favorite) {
        viewModelScope.launch {
            runCatching { Repository.removeFavorite(favorite.slug) }
            // Reflect the removal straight away, no need to leave the screen.
            _state.update {
                it.copy(
                    favorites = it.favorites.filterNot { entry -> entry.slug == favorite.slug },
                    toast = "Unfollowed ${favorite.name}"
                )
            }
        }
    }

    /** Download everything a followed creator has, page by page. */
    public fun downloadAll(favorite: AppDatabase.Favorite) {
        Graph.downloads.enqueueCreator(favorite.slug, favorite.name)
        _state.update { it.copy(toast = "Queued a full crawl of ${favorite.name}") }
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
