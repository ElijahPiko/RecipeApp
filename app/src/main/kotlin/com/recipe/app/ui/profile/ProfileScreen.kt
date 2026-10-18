package com.recipe.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recipe.app.viewmodel.ProfileUiState
import com.recipe.app.viewmodel.ProfileViewModel
import com.recipe.core.model.Category

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onLogout: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("个人资料", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = profile.nickname,
            onValueChange = { viewModel.update(profile.copy(nickname = it)) },
            label = { Text("昵称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = profile.signature,
            onValueChange = { viewModel.update(profile.copy(signature = it)) },
            label = { Text("签名") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = profile.location,
            onValueChange = { viewModel.update(profile.copy(location = it)) },
            label = { Text("所在地") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = profile.favoriteCategory,
            onValueChange = { viewModel.update(profile.copy(favoriteCategory = it)) },
            label = { Text("喜欢的分类") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saved) Text("已保存", color = MaterialTheme.colorScheme.primary)

        Button(
            onClick = { viewModel.saveProfile() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading
        ) {
            Text("保存资料")
        }

        if (onLogout != null) {
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        }

        Text("分类管理", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            "分类用于新建菜谱时下拉选择，以及按分类随机选菜。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryEditor(state, viewModel)
    }
}

@Composable
private fun CategoryEditor(state: ProfileUiState, viewModel: ProfileViewModel) {
    state.categories.forEach { category -> CategoryRow(category, state, viewModel) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.newCategoryName,
            onValueChange = viewModel::setNewCategoryName,
            label = { Text("新分类名") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { viewModel.addCategory() },
            enabled = state.newCategoryName.isNotBlank(),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加")
        }
    }
}

@Composable
private fun CategoryRow(category: Category, state: ProfileUiState, viewModel: ProfileViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.editingCategory?.id == category.id) {
                OutlinedTextField(
                    value = state.editName,
                    onValueChange = viewModel::setEditName,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.saveEditCategory() }, enabled = state.editName.isNotBlank()) { Text("保存") }
                TextButton(onClick = { viewModel.cancelEdit() }) { Text("取消") }
            } else {
                Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.startEdit(category) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { viewModel.deleteCategory(category) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
