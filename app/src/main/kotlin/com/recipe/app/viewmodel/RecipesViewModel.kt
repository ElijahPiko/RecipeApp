package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.app.data.OrderCartStore
import com.recipe.core.data.RecipeRepository
import com.recipe.core.model.OrderCart
import com.recipe.core.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecipesUiState(
    val search: String = "",
    val recipes: List<Recipe> = emptyList(),
    val categories: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class RecipesViewModel(
    private val repository: RecipeRepository,
    private val orderCart: OrderCartStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState.asStateFlow()

    /**
     * The live order cart. Exposed so the 点菜 FAB can show how many dishes are queued;
     * the store itself is activity-scoped, so the badge survives tab switches and returns.
     */
    val cart: StateFlow<OrderCart> = orderCart.cart

    init {
        loadCategories()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.search(_uiState.value.search) }
                .onSuccess { list -> _uiState.value = _uiState.value.copy(recipes = list, loading = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    fun onSearchChange(query: String) {
        _uiState.value = _uiState.value.copy(search = query)
        viewModelScope.launch {
            val list = repository.search(query)
            _uiState.value = _uiState.value.copy(recipes = list)
        }
    }

    fun addToOrder(recipe: Recipe) {
        orderCart.add(recipe)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { repository.categories() }
                .onSuccess { categories -> _uiState.value = _uiState.value.copy(categories = categories.map { it.name }) }
        }
    }
}
