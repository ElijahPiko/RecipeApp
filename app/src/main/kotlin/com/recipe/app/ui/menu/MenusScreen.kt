package com.recipe.app.ui.menu

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.recipe.app.R
import com.recipe.app.ui.components.CircularFab
import com.recipe.app.ui.recipe.crop.CropImageDialog
import com.recipe.app.ui.recipe.crop.ImageCrop
import com.recipe.app.viewmodel.MenusViewModel
import com.recipe.core.model.MenuRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom padding for the list so the last record clears the floating 材料 button. */
private val FabClearance = 88.dp

@Composable
fun MenusScreen(viewModel: MenusViewModel, onOpenMaterials: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 上传流程：点「上传照片」→ 系统选图 → 裁剪成 500×500 → 上传到当前这条记录。
    var photoTarget by remember { mutableStateOf<Long?>(null) }
    var pendingCrop by remember { mutableStateOf<Pair<Long, Bitmap>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = photoTarget
        if (uri != null && target != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = ImageCrop.decode(context.contentResolver, uri) ?: return@launch
                withContext(Dispatchers.Main) { pendingCrop = target to bitmap }
            }
        }
        photoTarget = null
    }

    // Refresh on every entry: returning from 材料账单 must show the new 总金额 immediately.
    LaunchedEffect(Unit) { viewModel.refresh() }

    if (state.loading && state.records.isEmpty() && state.materialStats.entryCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                StatsCard(state.stats.orderCount, state.totalSpend, state.averageSpend)
            }
            if (state.records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("暂无历史点菜记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.records, key = { it.id }) { record ->
                    MenuRecordCard(
                        record = record,
                        uploading = state.uploadingId == record.id,
                        onPickPhoto = {
                            photoTarget = record.id
                            imagePicker.launch("image/*")
                        }
                    )
                }
            }
        }

        // 材料账单入口：固定在右下角，不随列表滚动。
        CircularFab(
            onClick = onOpenMaterials,
            containerColor = MaterialTheme.colorScheme.surface,
            contentDescription = "材料账单",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_materials_basket),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }

    pendingCrop?.let { (recordId, source) ->
        CropImageDialog(
            source = source,
            onConfirm = { cropped ->
                viewModel.uploadPhoto(recordId, ImageCrop.toJpegBytes(cropped))
                source.recycle()
                pendingCrop = null
            },
            onCancel = {
                source.recycle()
                pendingCrop = null
            }
        )
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
private fun MenuRecordCard(
    record: MenuRecord,
    uploading: Boolean,
    onPickPhoto: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            // 有自己拍的照片就优先显示；没有则回落到这道菜的成品图。
            MenuPhoto(record)

            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTime(record.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onPickPhoto, enabled = !uploading) {
                        Text(
                            when {
                                uploading -> "上传中…"
                                record.hasPhoto -> "更换照片"
                                else -> "上传照片"
                            }
                        )
                    }
                }
                Text(
                    record.items.joinToString("、") { "${it.recipeName}×${it.quantity}" },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (record.estimatedCost > 0.0) {
                    // 下单时的预估成本快照，仅供回看；不参与顶部统计。
                    Text(
                        "预估成本 ¥${money(record.estimatedCost)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 记录自己的照片（按 16:9 展示）；没有照片时展示这次点过的菜的成品图缩略图。
 */
@Composable
private fun MenuPhoto(record: MenuRecord) {
    if (record.hasPhoto) {
        AsyncImage(
            model = record.photoUrl,
            contentDescription = "菜单照片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        record.items.take(5).forEach { item ->
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.recipeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun money(v: Double): String = "%.2f".format(v)

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epoch))
