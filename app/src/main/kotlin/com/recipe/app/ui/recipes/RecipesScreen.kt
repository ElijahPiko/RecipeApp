package com.recipe.app.ui.recipes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.recipe.app.R
import com.recipe.app.ui.components.RecipeCard
import com.recipe.app.viewmodel.RecipesViewModel
import kotlinx.coroutines.launch

/** Diameter of both floating action buttons. */
private val FabSize = 56.dp

/** Exact colour of the 点菜 artwork's disc, so the button outline blends into the icon. */
private val OrderFabOrange = Color(0xFFE4572E)

/** Bottom padding for scrollable content so the last row can clear the floating buttons. */
private val FabClearance = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onOpenOrder: () -> Unit,
    onNewRecipe: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Refresh whenever this screen (re)enters composition so newly created recipes
    // (including their freshly uploaded images) appear after coming back from 新建菜谱.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The 点菜 / 新建菜谱 entries now live in the floating buttons below, so the
            // search field gets the full width of the top bar.
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                placeholder = { Text("搜索菜谱或食材") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )

            when {
                state.loading && state.recipes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.error != null && state.recipes.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "加载失败：${state.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                    }
                }
                state.recipes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有菜谱，点击左下角 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        // Extra bottom padding keeps the last row scrollable above the FABs.
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = FabClearance
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onAdd = { r ->
                                    viewModel.addToOrder(r)
                                    scope.launch { snackbar.showSnackbar("已将「${r.name}」加入菜单") }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating actions: pinned to the screen corners, unaffected by scrolling.
        NewRecipeFab(
            onClick = onNewRecipe,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
        OrderFab(
            count = cart.totalQuantity,
            onClick = onOpenOrder,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )

        // Sit above the FABs instead of covering them.
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
        )
    }
}

/**
 * 点菜 entry point: the supplied artwork already is a circular icon (transparent
 * corners, solid orange disc), so it fills the button and the badge rides on the
 * top-right corner of the circle.
 */
@Composable
private fun OrderFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = if (count > 0) "点菜，已选 $count 道" else "点菜"
    BadgedBox(
        modifier = modifier,
        badge = {
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    ) {
        CircleFab(
            onClick = onClick,
            containerColor = OrderFabOrange,
            contentDescription = description
        ) {
            Image(
                painter = painterResource(R.drawable.ic_order_dishes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 新建菜谱 entry point: a plain circular button with a "+". */
@Composable
private fun NewRecipeFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CircleFab(
        onClick = onClick,
        modifier = modifier,
        // Neutral surface + brand orange glyph: clearly secondary to the 点菜 button
        // without picking up the (dynamic) theme's lavender primary container.
        containerColor = MaterialTheme.colorScheme.surface,
        contentDescription = "新建菜谱"
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = OrderFabOrange,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * A circular floating button.
 *
 * Built by hand instead of `FloatingActionButton` because the 点菜 icon must fill the
 * circle edge to edge. Press feedback is a slight scale-down: a ripple would be hidden
 * underneath an opaque icon anyway.
 */
@Composable
private fun CircleFab(
    onClick: () -> Unit,
    containerColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "fabScale")

    Box(
        modifier = modifier
            .size(FabSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            // Name the button on the same node that carries the click action, so TalkBack
            // reads one node instead of a clickable parent plus an unnamed child.
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                this.onClick(label = contentDescription) { onClick(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
