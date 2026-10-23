package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.app.data.OrderCartStore
import com.recipe.core.data.MaterialRepository
import com.recipe.core.data.MenuRepository
import com.recipe.core.data.RecipeRepository
import com.recipe.core.domain.AggregatedIngredients
import com.recipe.core.domain.DishEstimate
import com.recipe.core.domain.DishEstimator
import com.recipe.core.domain.IngredientsAggregator
import com.recipe.core.domain.MaterialPriceIndex
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderCart
import com.recipe.core.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrderUiState(
    val cart: OrderCart = OrderCart(),
    val recipes: Map<Long, Recipe> = emptyMap(),
    val aggregated: AggregatedIngredients = AggregatedIngredients(emptyList(), emptyList()),
    val categories: List<String> = emptyList(),
    val randomCategory: String = "",
    /** 每道菜的理论单价（材料均价 × 菜谱标注重量），按 recipeId 索引。 */
    val estimates: Map<Long, DishEstimate> = emptyMap(),
    /** 全车理论总价 = Σ(单价 × 数量)。仅作展示/记录，不影响菜单页总金额。 */
    val estimatedTotal: Double = 0.0,
    val loading: Boolean = false,
    val confirmed: Boolean = false,
    val error: String? = null
)

class OrderViewModel(
    private val recipeRepository: RecipeRepository,
    private val menuRepository: MenuRepository,
    private val orderCart: OrderCartStore,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderCart.cart.collect { refresh() }
        }
        loadCategories()
    }

    fun setRandomCategory(category: String) {
        _uiState.value = _uiState.value.copy(randomCategory = category, error = null)
    }

    fun randomSelect(count: Int = 3) {
        viewModelScope.launch {
            val cat = _uiState.value.randomCategory.ifBlank { null }
            val recipes = runCatching { recipeRepository.search("", cat) }.getOrElse { emptyList() }
            if (recipes.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = "该分类下暂无菜谱")
                return@launch
            }
            recipes.shuffled().take(count).forEach { orderCart.add(it) }
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { recipeRepository.categories() }
                .onSuccess { categories -> _uiState.value = _uiState.value.copy(categories = categories.map { it.name }) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val cart = orderCart.cart.value
            _uiState.value = _uiState.value.copy(cart = cart, loading = true)
            val recipes = cart.items.associate { item ->
                item.recipeId to runCatching { recipeRepository.get(item.recipeId) }.getOrNull()
            }.filterValues { it != null }.mapValues { it.value!! }
            val aggregated = IngredientsAggregator.aggregate(recipes.values.toList())

            // Theoretical prices: 材料均价（元/千克）× 菜谱上标注的重量（千克）。Failing to load
            // the price ledger must not break ordering, so prices degrade to "unknown".
            val priceIndex = MaterialPriceIndex(
                runCatching { materialRepository.prices() }.getOrDefault(emptyList())
            )
            val estimates = recipes.values.associate { it.id to DishEstimator.estimate(it, priceIndex) }
            val estimatedTotal = cart.items.sumOf { item ->
                (estimates[item.recipeId]?.unitPrice ?: 0.0) * item.quantity
            }

            _uiState.value = _uiState.value.copy(
                recipes = recipes,
                aggregated = aggregated,
                estimates = estimates,
                estimatedTotal = estimatedTotal,
                loading = false
            )
        }
    }

    fun updateQuantity(recipeId: Long, quantity: Int) {
        orderCart.updateQuantity(recipeId, quantity)
    }

    fun remove(recipeId: Long) {
        orderCart.remove(recipeId)
    }

    fun confirm() {
        val cart = orderCart.cart.value
        if (cart.isEmpty) return
        viewModelScope.launch {
            val record = MenuRecord(
                items = cart.items,
                // 手填花费保持 0：菜单页「总金额」取自材料账单，与这里的理论价无关。
                totalCost = 0.0,
                createdAt = System.currentTimeMillis(),
                // 理论价只做快照记录，方便日后回看，不参与任何统计。
                estimatedCost = _uiState.value.estimatedTotal
            )
            runCatching { menuRepository.create(record) }
                .onSuccess {
                    orderCart.clear()
                    _uiState.value = _uiState.value.copy(confirmed = true, error = null)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "下单失败") }
        }
    }

    fun resetConfirmed() {
        _uiState.value = _uiState.value.copy(confirmed = false)
    }
}
