package pl.bkacala.threecitycommuter.ui.screen.map.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.BusSearchBar(
    searchBarModel: SearchBarModel,
) {
    val query = searchBarModel.query.collectAsStateWithLifecycle().value
    val isActive = searchBarModel.isActive.collectAsStateWithLifecycle().value
    SearchBar(
        query = query,
        onQueryChange = { searchBarModel.onQueryChanged(it) },
        onSearch = { },
        active = isActive,
        onActiveChange = { searchBarModel.onSearchBarActiveChange() },
        modifier = Modifier.align(Alignment.TopCenter),
        trailingIcon = {
            if (isActive && query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = "kasowajka",
                    modifier = Modifier.clickable {
                        searchBarModel.onQueryChanged("")
                    }
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "szukajka"
            )
        },
        placeholder = { Text(text = "Szukaj przystanku") },
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        val searchResult = searchBarModel.results.collectAsStateWithLifecycle().value
        LazyColumn {
            items(searchResult) {
                it.Widget()
            }
        }
    }
}