package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.app.data.ImageUploader
import com.recipe.core.data.RecipeRepository
import com.recipe.core.domain.RecipeValidator
import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewRecipeUiState(
    val name: String = "",
    val preview: ImageBitmap? = null,
    val imageBytes: ByteArray? = null,
    val category: String = "",
    val categories: List<String> = emptyList(),
    val materials: List<Ingredient> = listOf(Ingredient("", "")),
    val seasonings: List<Ingredient> = listOf(Ingredient("", "")),
    val steps: List<String> = listOf(""),
    val errors: List<String> = emptyList(),
    val saving: Boolean = false,
    val savedId: Long? = null
)

class NewRecipeViewModel(
    private val repository: RecipeRepository,
    private val imageUploader: ImageUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewRecipeUiState())
    val uiState: StateFlow<NewRecipeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.categories() }
                .onSuccess { _uiState.value = _uiState.value.copy(categories = it.map { c -> c.name }) }
        }
    }

    fun setName(name: String) = update { it.copy(name = name) }
    fun setImageBytes(bytes: ByteArray?, preview: ImageBitmap? = null) = update {
        it.copy(imageBytes = bytes, preview = preview)
    }
    fun setCategory(category: String) = update { it.copy(category = category) }

    fun updateMaterial(index: Int, ingredient: Ingredient) = update {
        it.copy(materials = it.materials.mapIndexed { i, m -> if (i == index) ingredient else m })
    }
    fun addMaterial() = update { it.copy(materials = it.materials + Ingredient("", "")) }
    fun removeMaterial(index: Int) = update {
        it.copy(materials = it.materials.filterIndexed { i, _ -> i != index })
    }

    fun updateSeasoning(index: Int, ingredient: Ingredient) = update {
        it.copy(seasonings = it.seasonings.mapIndexed { i, m -> if (i == index) ingredient else m })
    }
    fun addSeasoning() = update { it.copy(seasonings = it.seasonings + Ingredient("", "")) }
    fun removeSeasoning(index: Int) = update {
        it.copy(seasonings = it.seasonings.filterIndexed { i, _ -> i != index })
    }

    fun updateStep(index: Int, text: String) = update {
        it.copy(steps = it.steps.mapIndexed { i, s -> if (i == index) text else s })
    }
    fun addStep() = update { it.copy(steps = it.steps + "") }
    fun removeStep(index: Int) = update {
        it.copy(steps = it.steps.filterIndexed { i, _ -> i != index })
    }

    fun save() {
        val state = _uiState.value
        val recipe = Recipe(
            name = state.name.trim(),
            imageUrl = "",
            hasImage = state.imageBytes != null,
            category = state.category.trim(),
            foodMaterials = state.materials.map { it.copy(name = it.name.trim(), amount = it.amount.trim()) }
                .filter { it.name.isNotBlank() },
            seasonings = state.seasonings.map { it.copy(name = it.name.trim(), amount = it.amount.trim()) }
                .filter { it.name.isNotBlank() },
            steps = state.steps.map { it.trim() }.filter { it.isNotBlank() },
            createdAt = System.currentTimeMillis()
        )
        val errors = RecipeValidator.validate(recipe)
        if (errors.isNotEmpty()) {
            update { it.copy(errors = errors) }
            return
        }
        _uiState.value = _uiState.value.copy(saving = true, errors = emptyList())
        viewModelScope.launch {
            runCatching {
                val saved = repository.save(recipe)
                state.imageBytes?.let { imageUploader.upload(saved.id, it) }
                saved
            }
            .onSuccess { saved -> _uiState.value = _uiState.value.copy(saving = false, savedId = saved.id) }
            .onFailure { e -> _uiState.value = _uiState.value.copy(saving = false, errors = listOf(e.message ?: "保存失败")) }
        }
    }

    private fun update(transform: (NewRecipeUiState) -> NewRecipeUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
