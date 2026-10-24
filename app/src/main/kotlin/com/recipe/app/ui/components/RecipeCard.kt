package com.recipe.app.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import com.recipe.core.model.Recipe

/**
 * 菜谱卡片。短按不做任何事，**长按进入编辑页**（[onLongClick]，传 null 表示不可编辑）。
 */
@Composable
fun RecipeCard(
    recipe: Recipe,
    onAdd: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Recipe) -> Unit)? = null
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = modifier.fillMaxWidth().then(
            if (onLongClick == null) {
                Modifier
            } else {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClickLabel = "编辑菜谱",
                    onLongClick = { onLongClick(recipe) }
                )
            }
        )
    ) {
        Box {
            if (recipe.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = recipe.imageUrl,
                    contentDescription = recipe.name,
                    contentScale = ContentScale.Crop,
                    placeholder = remember { ColorPainter(placeholderColor) },
                    error = rememberVectorPainter(Icons.Filled.BrokenImage),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍲", style = MaterialTheme.typography.displayMedium)
                }
            }
            FilledIconButton(
                onClick = { onAdd(recipe) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(44.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "加入菜单")
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CategoryChip(recipe.category)
            }
            Text(
                text = if (recipe.foodMaterials.isEmpty()) "暂无配料" else
                    recipe.foodMaterials.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun CategoryChip(category: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
