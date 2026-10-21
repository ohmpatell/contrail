package com.contrail.contrail.feature.airports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.NewsRepository
import com.contrail.contrail.core.data.repository.WatchlistRepository
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.network.news.NewsArticle
import com.contrail.contrail.core.network.news.NewsFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A news filter chip: everything, or one airport or airline. */
data class NewsFilter(val label: String, val feed: NewsFeed)

data class AirportsUiState(
    val query: String = "",
    val results: List<AirportWithUser> = emptyList(),
    val favourites: List<AirportWithUser> = emptyList(),
    val visited: List<AirportWithUser> = emptyList(),
    val filters: List<NewsFilter> = listOf(AllNews),
    val selectedFilter: NewsFilter = AllNews,
    val news: List<NewsArticle> = emptyList(),
    val newsLoading: Boolean = false,
) {
    companion object {
        val AllNews = NewsFilter("All aviation", NewsFeed.Aviation)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AirportsViewModel @Inject constructor(
    private val airports: AirportRepository,
    private val newsRepo: NewsRepository,
    watchlist: WatchlistRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AirportsUiState.AllNews)
    private val loading = MutableStateFlow(false)

    private val results = query.debounce(120).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else airports.search(q)
    }

    /** Your favourite airports and watched airlines become news filters. */
    private val filters = combine(airports.favourites(), watchlist.observe()) { favs, watch ->
        buildList {
            add(AirportsUiState.AllNews)
            favs.forEach { add(NewsFilter(it.city.ifBlank { it.iata }, NewsFeed.About(newsName(it)))) }
            watch.filter { it.kind == WatchKind.Airline }.forEach { add(NewsFilter(it.label, NewsFeed.About(it.label))) }
            DEFAULT_AIRLINES.filter { name -> none { it.label == name } }.forEach { add(NewsFilter(it, NewsFeed.About(it))) }
        }.distinctBy { it.feed.key }
    }

    private val news = filter.flatMapLatest { f ->
        viewModelScope.launch {
            loading.value = true
            newsRepo.refresh(f.feed)
            loading.value = false
        }
        newsRepo.observe(f.feed)
    }

    private val mine = combine(airports.favourites(), airports.visited()) { f, v -> f to v }

    val state: StateFlow<AirportsUiState> = combine(query, results, mine, combine(filters, filter) { a, b -> a to b }, combine(news, loading) { n, l -> n to l }) { q, r, (favs, visited), (fs, sel), (n, l) ->
        AirportsUiState(q, r, favs, visited, fs, sel, n, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AirportsUiState())

    init {
        viewModelScope.launch { airports.ensureImported() }
    }

    fun onQuery(q: String) {
        query.value = q
    }

    fun onFilter(f: NewsFilter) {
        filter.value = f
    }

    fun refreshNews() {
        viewModelScope.launch {
            loading.value = true
            newsRepo.refresh(filter.value.feed, force = true)
            loading.value = false
        }
    }

    fun toggleFavourite(a: AirportWithUser) {
        viewModelScope.launch { airports.setFavourite(a.icao, a.favourite != true) }
    }

    companion object {
        /** Quick airline filters before you've watched any. */
        val DEFAULT_AIRLINES = listOf("Etihad Airways", "Cathay Pacific", "Air Canada")

        /** "Toronto Pearson" reads better in news searches than the full official name. */
        fun newsName(a: AirportWithUser): String {
            val short = a.name.replace(" International Airport", "").replace(" Airport", "").replace(" International", "")
            return if (short.contains(a.city, ignoreCase = true) || a.city.isBlank()) "$short airport" else "${a.city} $short airport"
        }
    }
}
