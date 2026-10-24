package com.recipe.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.recipe.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: (() -> Unit)? = null,
    onManageCategories: () -> Unit = {}
) {
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

        // 分类管理不再在这页展开，只保留一个入口，点进去在独立页面里编辑。
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onManageCategories)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "分类管理",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (onLogout != null) {
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
