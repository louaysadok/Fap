package com.fap.app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fap.core.TagRef
import com.fap.app.ui.components.LoadingRow
import com.fap.app.ui.components.MessageCard

/**
 * The site's tags (/tags/): browse the listed tags or type to autocomplete
 * from `/ajax/tags/suggest/`, then open a tag's media feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TagsScreen(
    viewModel: TagsViewModel,
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val typedSlug = viewModel.slugFor(state.query)
    val searching = state.query.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Tags #",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text("Search tags") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { if (typedSlug.isNotEmpty()) onOpenTag(typedSlug) }
            ),
            trailingIcon = {
                IconButton(
                    onClick = { if (typedSlug.isNotEmpty()) onOpenTag(typedSlug) },
                    enabled = typedSlug.isNotEmpty()
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Open tag")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                !searching && state.loading && state.tags.isEmpty() -> LoadingRow("Loading tags…")
                !searching && state.error != null && state.tags.isEmpty() ->
                    MessageCard(state.error ?: "Something went wrong") {
                        Button(onClick = viewModel::refresh) { Text("Retry") }
                    }
                searching -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    if (typedSlug.isNotEmpty() && state.suggestions.none { it.slug == typedSlug }) {
                        item {
                            TagRow(
                                tag = TagRef(typedSlug, state.query.trim()),
                                label = "Open tag \"${state.query.trim()}\"",
                                onClick = { onOpenTag(typedSlug) }
                            )
                        }
                    }
                    items(state.suggestions, key = { tag -> tag.slug }) { tag ->
                        TagRow(tag = tag, onClick = { onOpenTag(tag.slug) })
                    }
                    if (state.suggesting) {
                        item { LoadingRow("Searching tags…") }
                    } else if (state.suggestions.isEmpty() && typedSlug.isEmpty()) {
                        item { MessageCard("Keep typing to search tags.") }
                    } else if (state.suggestions.isEmpty()) {
                        item { MessageCard("No matching tags — hit search to try it anyway.") }
                    }
                }
                state.tags.isEmpty() -> MessageCard("No tags listed right now.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(state.tags, key = { tag -> tag.slug }) { tag ->
                        TagRow(tag = tag, onClick = { onOpenTag(tag.slug) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: TagRef,
    label: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label ?: tag.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
