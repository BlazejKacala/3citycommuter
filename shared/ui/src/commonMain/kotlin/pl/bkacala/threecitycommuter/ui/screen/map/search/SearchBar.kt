package pl.bkacala.threecitycommuter.ui.screen.map.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.BusSearchBar(
    query: String,
    isActive: Boolean,
    results: List<SearchResultRowModel>,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onResultClick: (Int) -> Unit,
) {
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = isActive,
                onExpandedChange = onExpandedChange,
                trailingIcon = {
                    if (isActive && query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "kasowajka",
                            modifier = Modifier.clickable {
                                onQueryChange("")
                            },
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "szukajka",
                    )
                },
                placeholder = { Text(text = "Szukaj przystanku") },
            )
        },
        expanded = isActive,
        onExpandedChange = onExpandedChange,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        LazyColumn {
            items(results) {
                it.Widget(onClicked = onResultClick)
            }
        }
    }
}
