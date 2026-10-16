package com.recipe.app.ui.menu

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.recipe.app.viewmodel.MenusViewModel
import com.recipe.core.model.MenuRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MenusScreen(viewModel: MenusViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (state.loading && state.records.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            StatsCard(state.stats.orderCount, state.stats.totalCost, state.stats.averageCost)
        }
        if (state.records.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("暂无历史点菜记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.records, key = { it.id }) { record ->
                MenuRecordCard(record, onSaveCost = { cost -> viewModel.updateCost(record.id, cost) })
            }
        }
    }
}

@Composable
private fun StatsCard(count: Int, total: Double, average: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Stat("点菜次数", count.toString())
            Stat("总金额", "¥${money(total)}")
            Stat("平均花费", "¥${money(average)}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun MenuRecordCard(record: MenuRecord, onSaveCost: (Double) -> Unit) {
    var costText by remember(record.id) { mutableStateOf(if (record.totalCost == 0.0) "" else record.totalCost.toString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(formatTime(record.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                record.items.joinToString("、") { "${it.recipeName}×${it.quantity}" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("本次花费") },
                    prefix = { Text("¥ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onSaveCost(costText.toDoubleOrNull() ?: 0.0) },
                    enabled = costText.isNotBlank(),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

private fun money(v: Double): String = "%.2f".format(v)

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epoch))
