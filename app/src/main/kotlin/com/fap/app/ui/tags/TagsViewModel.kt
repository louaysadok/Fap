package com.fap.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fap.app.repo.Repository
import com.fap.core.TagRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class TagsUiState(
    val tags: List<TagRef> = emptyList(),
    val suggestions: List<TagRef> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val suggesting: Boolean = false,
    val error: String? = null
)

/**
 * The /tags/ index, plus live autocomplete from `/ajax/tags/suggest/` —
 * the same endpoint the site's own tag pages use.
 */
public class TagsViewModel : ViewModel() {

    private val _state = MutableStateFlow(TagsUiState())
    public val state: StateFlow<TagsUiState> get() = _state

    private var suggestJob: Job? = null

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tags = Repository.tags()
                _state.update { it.copy(tags = tags, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load tags") }
            }
        }
    }

    public fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        suggestJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(suggestions = emptyList(), suggesting = false) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(suggesting = true) }
            runCatching { Repository.tagSuggestions(value.trim()) }
                .onSuccess { suggestions -> _state.update { it.copy(suggestions = suggestions, suggesting = false) } }
                .onFailure { _state.update { it.copy(suggestions = emptyList(), suggesting = false) } }
        }
    }

    /** "ball gag" -> "ball-gag", for opening a typed tag directly. */
    public fun slugFor(raw: String): String =
        raw.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
