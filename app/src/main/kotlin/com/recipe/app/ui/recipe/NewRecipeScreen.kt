package com.recipe.app.ui.recipe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recipe.app.ui.recipe.crop.CropImageDialog
import com.recipe.app.ui.recipe.crop.ImageCrop
import com.recipe.app.viewmodel.NewRecipeViewModel
import com.recipe.app.viewmodel.NewRecipeUiState
import com.recipe.core.model.Ingredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecipeScreen(
    viewModel: NewRecipeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCrop by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = ImageCrop.decode(context.contentResolver, uri)
                withContext(Dispatchers.Main) { pendingCrop = bitmap }
            }
        }
    }

    if (state.savedId != null) {
        onSaved()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建菜谱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }, enabled = !state.saving) {
                        Text(if (state.saving) "保存中…" else "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImageField(state.preview, onPick = { imagePicker.launch("image/*") })
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("菜名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CategoryDropdown(state, viewModel::setCategory)

            SectionTitle("食品材料")
            IngredientEditor(
                state.materials,
                onUpdate = viewModel::updateMaterial,
                onAdd = viewModel::addMaterial,
                onRemove = viewModel::removeMaterial
            )

            SectionTitle("调料")
            IngredientEditor(
                state.seasonings,
                onUpdate = viewModel::updateSeasoning,
                onAdd = viewModel::addSeasoning,
                onRemove = viewModel::removeSeasoning
            )

            SectionTitle("操作步骤")
            StepsEditor(
                state.steps,
                onUpdate = viewModel::updateStep,
                onAdd = viewModel::addStep,
                onRemove = viewModel::removeStep
            )

            if (state.errors.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        state.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving
            ) {
                Text("保存菜谱")
            }
        }
    }

    pendingCrop?.let { source ->
        CropImageDialog(
            source = source,
            onConfirm = { cropped ->
                val bytes = ImageCrop.toJpegBytes(cropped)
                viewModel.setImageBytes(bytes, cropped.asImageBitmap())
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
private fun ImageField(preview: ImageBitmap?, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (preview == null) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    placeholder = { Text("成品图（点击选择）") },
                    leadingIcon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "成品图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }
            TextButton(onClick = onPick) { Text(if (preview == null) "选择图片" else "更换图片") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun IngredientEditor(
    items: List<Ingredient>,
    onUpdate: (Int, Ingredient) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { onUpdate(index, item.copy(name = it)) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.amount,
                    onValueChange = { onUpdate(index, item.copy(amount = it)) },
                    label = { Text("用量") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加一项")
        }
    }
}

@Composable
private fun StepsEditor(
    steps: List<String>,
    onUpdate: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", modifier = Modifier.padding(start = 4.dp))
                OutlinedTextField(
                    value = step,
                    onValueChange = { onUpdate(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    placeholder = { Text("步骤说明", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加步骤")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(state: NewRecipeUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.category,
            onValueChange = {},
            readOnly = true,
            label = { Text("分类 *") },
            placeholder = { Text("请选择分类") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.categories.forEach { category ->
                DropdownMenuItem(text = { Text(category) }, onClick = {
                    onSelect(category)
                    expanded = false
                })
            }
        }
    }
}
