package ch.pianonic.pauxb.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.pianonic.pauxb.data.AppCatalog
import ch.pianonic.pauxb.data.CatalogApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    installedAppIds: Set<String>,
    installingAppIds: Set<String>,
    onInstallApp: (name: String, packageName: String, command: String) -> Unit,
    onSearchDebian: (query: String) -> Unit,
    debianSearchResults: List<DebianSearchResult>,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showDebianSearch by remember { mutableStateOf(false) }

    val catalogResults = remember(searchQuery, selectedCategory) {
        when {
            searchQuery.isNotBlank() -> AppCatalog.search(searchQuery)
            selectedCategory != null -> AppCatalog.getByCategory(selectedCategory!!)
            else -> AppCatalog.apps
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("App Store") })
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        selectedCategory = null
                        showDebianSearch = false
                    },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AppCatalog.categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                                searchQuery = ""
                                showDebianSearch = false
                            },
                            label = { Text(category) }
                        )
                    }
                }
            }

            // Catalog results
            items(catalogResults) { app ->
                val appId = app.name.lowercase().replace(" ", "_")
                val isInstalled = appId in installedAppIds
                val isInstalling = appId in installingAppIds

                CatalogAppCard(
                    app = app,
                    isInstalled = isInstalled,
                    isInstalling = isInstalling,
                    onInstall = { onInstallApp(app.name, app.packageName, app.command) }
                )
            }

            // No catalog results + search query = offer Debian search
            if (catalogResults.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Not in catalog",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Search Debian repositories for \"$searchQuery\"?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    showDebianSearch = true
                                    onSearchDebian(searchQuery)
                                }
                            ) {
                                Text("Search Debian Packages")
                            }
                        }
                    }
                }
            }

            // Debian search results
            if (showDebianSearch) {
                if (isSearching) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Searching Debian packages...")
                        }
                    }
                }

                if (debianSearchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Debian Packages",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(debianSearchResults) { result ->
                        val appId = result.name.lowercase().replace(" ", "_")
                        val isInstalled = appId in installedAppIds
                        val isInstalling = appId in installingAppIds

                        DebianResultCard(
                            result = result,
                            isInstalled = isInstalled,
                            isInstalling = isInstalling,
                            onInstall = { onInstallApp(result.name, result.packageName, result.packageName) }
                        )
                    }
                }

                if (!isSearching && debianSearchResults.isEmpty() && showDebianSearch) {
                    item {
                        Text(
                            text = "No packages found for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

data class DebianSearchResult(
    val name: String,
    val packageName: String,
    val description: String
)

@Composable
private fun CatalogAppCard(
    app: CatalogApp,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.packageName,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                isInstalling -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                isInstalled -> {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    Button(onClick = onInstall) {
                        Text("Install")
                    }
                }
            }
        }
    }
}

@Composable
private fun DebianResultCard(
    result: DebianSearchResult,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = result.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Text(
                    text = result.packageName,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                isInstalling -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                isInstalled -> {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    Button(onClick = onInstall) {
                        Text("Install")
                    }
                }
            }
        }
    }
}
