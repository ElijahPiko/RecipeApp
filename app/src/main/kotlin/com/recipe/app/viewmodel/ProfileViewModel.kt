package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.core.data.RecipeRepository
import com.recipe.core.data.ProfileRepository
import com.recipe.core.model.Category
import com.recipe.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val categories: List<Category> = emptyList(),
    val newCategoryName: String = "",
    val editingCategory: Category? = null,
    val editName: String = "",
    val loading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(loading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.get() }
                .onSuccess { profile -> _uiState.value = _uiState.value.copy(profile = profile, loading = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "加载失败") }
        }
        loadCategories()
    }

    fun update(profile: UserProfile) {
        _uiState.value = _uiState.value.copy(profile = profile, saved = false)
    }

    fun saveProfile() {
        viewModelScope.launch {
            runCatching { repository.save(_uiState.value.profile) }
                .onSuccess { saved -> _uiState.value = _uiState.value.copy(profile = saved, saved = true) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "保存失败") }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { recipeRepository.categories() }
                .onSuccess { categories -> _uiState.value = _uiState.value.copy(categories = categories, error = null) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "分类加载失败") }
        }
    }

    fun setNewCategoryName(name: String) = mutate { it.copy(newCategoryName = name) }

    fun addCategory() {
        val name = _uiState.value.newCategoryName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { recipeRepository.createCategory(Category(name = name)) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(newCategoryName = "", error = null)
                    loadCategories()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "添加分类失败") }
        }
    }

    fun startEdit(category: Category) = mutate {
        it.copy(editingCategory = category, editName = category.name)
    }

    fun setEditName(name: String) = mutate { it.copy(editName = name) }

    fun cancelEdit() = mutate { it.copy(editingCategory = null, editName = "") }

    fun saveEditCategory() {
        val editing = _uiState.value.editingCategory ?: return
        val name = _uiState.value.editName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { recipeRepository.updateCategory(editing.copy(name = name)) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(editingCategory = null, editName = "", error = null)
                    loadCategories()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "修改分类失败") }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            runCatching { recipeRepository.deleteCategory(category.id) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(editingCategory = null, error = null)
                    loadCategories()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "删除分类失败") }
        }
    }

    private fun mutate(transform: (ProfileUiState) -> ProfileUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
