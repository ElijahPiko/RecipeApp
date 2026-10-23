package com.recipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipe.core.data.MaterialRepository
import com.recipe.core.data.MenuRepository
import com.recipe.core.domain.MenuStats
import com.recipe.core.model.MaterialStats
import com.recipe.core.model.MenuRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MenusUiState(
    val stats: MenuStats = MenuStats(0, 0.0, 0.0),
    val records: List<MenuRecord> = emptyList(),
    val materialStats: MaterialStats = MaterialStats(),
    val loading: Boolean = false,
    /** 正在上传照片的记录 id，用来禁用按钮、避免重复上传。 */
    val uploadingId: Long? = null,
    val error: String? = null
) {
    /**
     * 顶部「总金额」= 材料账单累计支出（不是各次菜单记录里手填的花费合计）。
     */
    val totalSpend: Double get() = materialStats.totalPrice

    /** 平均花费 = 总金额 ÷ 点菜次数。 */
    val averageSpend: Double get() = if (stats.orderCount == 0) 0.0 else totalSpend / stats.orderCount
}

class MenusViewModel(
    private val repository: MenuRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenusUiState(loading = true))
    val uiState: StateFlow<MenusUiState> = _uiState.asStateFlow()

    /**
     * Driven by the screen on every (re)entry, so the ledger total stays in step after the
     * user records purchases on the 材料账单 page and comes back.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { Triple(repository.list(), repository.stats(), materialRepository.stats()) }
                .onSuccess { (records, stats, materialStats) ->
                    _uiState.value = _uiState.value.copy(
                        records = records,
                        stats = stats,
                        materialStats = materialStats,
                        loading = false
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    /** 上传一张自己拍的菜单照片，成功后重新拉列表（服务端会返回新的缓存版本号）。 */
    fun uploadPhoto(id: Long, bytes: ByteArray) {
        if (_uiState.value.uploadingId != null) return
        _uiState.value = _uiState.value.copy(uploadingId = id, error = null)
        viewModelScope.launch {
            runCatching { repository.uploadPhoto(id, bytes) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(uploadingId = null)
                    refresh()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(uploadingId = null, error = e.message ?: "照片上传失败")
                }
        }
    }
}
