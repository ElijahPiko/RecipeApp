package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.core.data.MenuRepository
import com.recipe.core.domain.MenuStats
import com.recipe.core.model.MenuRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MenusUiState(
    val stats: MenuStats = MenuStats(0, 0.0, 0.0),
    val records: List<MenuRecord> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class MenusViewModel(private val repository: MenuRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MenusUiState(loading = true))
    val uiState: StateFlow<MenusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.list() to repository.stats() }
                .onSuccess { (records, stats) ->
                    _uiState.value = _uiState.value.copy(records = records, stats = stats, loading = false)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    fun updateCost(id: Long, totalCost: Double) {
        viewModelScope.launch {
            runCatching { repository.updateCost(id, totalCost) }
                .onSuccess { refresh() }
        }
    }
}
