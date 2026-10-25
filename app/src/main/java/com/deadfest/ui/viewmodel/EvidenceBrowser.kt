package com.deadfest.ui.viewmodel

import com.deadfest.data.model.DeathEvidence
import com.deadfest.data.repository.FirebaseRepository
import com.deadfest.data.repository.FirebaseRepository.EvidenceFilter
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paged, filterable list of death evidence for the Deaths Images gallery and the admin review
 * screen (v2.1.3). Pages of [pageSize] come from the server with the filter applied there, so a
 * collection of thousands of screenshots loads no faster or slower than one of twenty.
 */
class EvidenceBrowser(
    private val repository: FirebaseRepository,
    private val scope: CoroutineScope,
    initial: EvidenceFilter,
    private val describe: (Throwable) -> String,
    private val pageSize: Long = 24
) {
    data class State(
        val filter: EvidenceFilter,
        val items: List<DeathEvidence> = emptyList(),
        val loading: Boolean = false,
        val loadedOnce: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State(initial))
    val state: StateFlow<State> = _state.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private var job: Job? = null

    fun setFilter(filter: EvidenceFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun refresh() {
        job?.cancel()
        cursor = null
        _state.update { it.copy(items = emptyList(), endReached = false, error = null, loading = false) }
        load()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.error != null || job?.isActive == true) return
        load()
    }

    private fun load() {
        val filter = _state.value.filter
        job = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val page = repository.evidencePage(filter, cursor, pageSize)
                cursor = page.cursor
                _state.update { s ->
                    val known = s.items.map { it.id }.toSet()
                    s.copy(
                        items = s.items + page.items.filter { it.id !in known },
                        loading = false,
                        loadedOnce = true,
                        endReached = page.endReached
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadedOnce = true, error = describe(e)) }
            }
        }
    }

    /** Swaps in a fresh copy after a moderation action, or drops it if it no longer matches the filter. */
    fun replace(item: DeathEvidence) {
        val f = _state.value.filter
        val matches = (f.status == null || f.status == item.status) &&
            (f.status != null || f.statuses.isNullOrEmpty() || item.status in f.statuses) &&
            (f.gameId == null || f.gameId == item.gameId) &&
            (f.subjectUid == null || f.subjectUid == item.subjectUid)
        _state.update { s ->
            s.copy(items = if (matches) s.items.map { if (it.id == item.id) item else it } else s.items.filter { it.id != item.id })
        }
    }

    fun remove(id: String) {
        _state.update { s -> s.copy(items = s.items.filter { it.id != id }) }
    }
}
