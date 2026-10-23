package com.recipe.app.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recipe.app.viewmodel.OrderUiState
import com.recipe.app.viewmodel.OrderViewModel
import com.recipe.core.domain.AggregatedIngredients
import com.recipe.core.domain.DishEstimate
import com.recipe.core.model.OrderItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onConfirmed: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showIngredients by remember { mutableStateOf(false) }

    // Navigate from a coroutine, not during composition: calling onConfirmed() directly
    // inside the composable body runs navController.navigate() mid-composition and
    // repeatedly on every recomposition (confirmed stays true during the transition),
    // which corrupts the NavController's back stack state — afterwards, tab navigation
    // back to 菜谱 silently becomes a no-op.
    LaunchedEffect(state.confirmed) {
        if (state.confirmed) {
            onConfirmed()
            viewModel.resetConfirmed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点菜") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilledTonalButton(
                onClick = { showIngredients = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("一键输出所需材料")
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RandomCategoryDropdown(state, viewModel)
                Button(
                    onClick = { viewModel.randomSelect() },
                    modifier = Modifier.weight(1f)
                ) { Text("随机选菜") }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.cart.isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("点菜车是空的，去菜谱页添加吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                EstimatedTotalCard(total = state.estimatedTotal, dishCount = state.cart.items.size)
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.cart.items, key = { it.recipeId }) { item ->
                        CartItemRow(
                            item = item,
                            estimate = state.estimates[item.recipeId],
                            viewModel = viewModel
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.confirm() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.cart.isEmpty
                    ) {
                        Text("确认下单 / 保存菜单")
                    }
                }
            }
        }
    }

    if (showIngredients) {
        IngredientsDialog(state.aggregated, onDismiss = { showIngredients = false })
    }
}

@Composable
private fun RandomCategoryDropdown(state: OrderUiState, viewModel: OrderViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (state.randomCategory.isBlank()) "全部分类" else state.randomCategory)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部分类") }, onClick = {
                viewModel.setRandomCategory("")
                expanded = false
            })
            state.categories.forEach { category ->
                DropdownMenuItem(text = { Text(category) }, onClick = {
                    viewModel.setRandomCategory(category)
                    expanded = false
                })
            }
        }
    }
}

/**
 * 预估总价：Σ(每道菜预估单价 × 数量)。只做展示与记录，不参与菜单页「总金额」。
 */
@Composable
private fun EstimatedTotalCard(total: Double, dishCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("预估总价", style = MaterialTheme.typography.labelLarge)
                Text(
                    "$dishCount 道菜 · 材料均价 × 菜谱用量，仅供参考",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "¥${money(total)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CartItemRow(item: OrderItem, estimate: DishEstimate?, viewModel: OrderViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.recipeName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.updateQuantity(item.recipeId, item.quantity - 1) }) { Text("-") }
                Text("${item.quantity}", modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = { viewModel.updateQuantity(item.recipeId, item.quantity + 1) }) { Text("+") }
                TextButton(onClick = { viewModel.remove(item.recipeId) }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            }
            // 单独一行，避免被右侧的加减按钮挤成两行。
            Text(
                estimatedLabel(estimate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 「预估 ¥12.34/份」— plus the names of the materials that could not be priced, so a low
 * number is explained instead of silently looking like a bargain. When everything is priced
 * the suffix is dropped entirely.
 */
private fun estimatedLabel(estimate: DishEstimate?): String {
    if (estimate == null) return "预估 ¥0.00/份"
    val base = "预估 ¥${money(estimate.unitPrice)}/份"
    if (estimate.unpricedMaterials.isEmpty()) {
        // 菜谱压根没填材料时，别让 ¥0.00 看起来像"白送"。
        return if (estimate.priced.isEmpty()) "$base · 菜谱未标注重量" else base
    }
    return "$base · ${estimate.unpricedMaterials.joinToString("、")}未计价"
}

private fun money(v: Double): String = "%.2f".format(v)

@Composable
private fun IngredientsDialog(aggregated: AggregatedIngredients, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("所需材料清单") },
        text = {
            Column {
                Text("食品材料", style = MaterialTheme.typography.titleSmall)
                aggregated.foodMaterials.forEach { Text("  ·  ${it.name}  ${it.amount}") }
                Text("调料", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                aggregated.seasonings.forEach { Text("  ·  ${it.name}  ${it.amount}") }
                if (aggregated.foodMaterials.isEmpty() && aggregated.seasonings.isEmpty()) {
                    Text("暂无可输出的材料")
                }
            }
        }
    )
}
