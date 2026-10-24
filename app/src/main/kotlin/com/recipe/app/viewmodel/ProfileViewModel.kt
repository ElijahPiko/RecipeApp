package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.core.data.ProfileRepository
import com.recipe.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val loading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

/**
 * 个人资料页。分类增删改已经搬到独立的 [CategoriesViewModel]（分类管理页），
 * 这里只负责个人信息。
 */
class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

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
}
