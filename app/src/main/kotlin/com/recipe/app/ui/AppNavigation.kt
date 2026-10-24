package com.recipe.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recipe.app.RecipeApplication
import com.recipe.app.data.AppContainer
import com.recipe.app.ui.auth.AuthScreen
import com.recipe.app.ui.categories.CategoriesScreen
import com.recipe.app.ui.materials.MaterialsScreen
import com.recipe.app.ui.menu.MenusScreen
import com.recipe.app.ui.order.OrderScreen
import com.recipe.app.ui.profile.ProfileScreen
import com.recipe.app.ui.recipe.RecipeEditorScreen
import com.recipe.app.ui.recipes.RecipesScreen
import com.recipe.app.viewmodel.CategoriesViewModel
import com.recipe.app.viewmodel.MaterialsViewModel
import com.recipe.app.viewmodel.MenusViewModel
import com.recipe.app.viewmodel.OrderViewModel
import com.recipe.app.viewmodel.ProfileViewModel
import com.recipe.app.viewmodel.RecipeEditorViewModel
import com.recipe.app.viewmodel.RecipesViewModel

private object Routes {
    const val RECIPES = "recipes"
    const val MENUS = "menus"
    const val PROFILE = "profile"
    const val ORDER = "order"
    const val NEW_RECIPE = "newRecipe"
    const val RECIPE_EDIT = "recipeEdit/{recipeId}"
    const val MATERIALS = "materials"
    const val CATEGORIES = "categories"
}

/** 长按菜谱卡片时用：拼出带 id 的编辑页路由。 */
private fun recipeEditRoute(recipeId: Long): String = "recipeEdit/$recipeId"

@Composable
fun AppNavigation(container: AppContainer) {
    // Session state: no stored token -> start at the auth screen.
    var loggedIn by rememberSaveable { mutableStateOf(container.tokenStore.hasToken()) }

    if (!loggedIn) {
        AuthScreen(container = container, onSuccess = { loggedIn = true })
        return
    }

    MainScaffold(container = container, onLoggedOut = { loggedIn = false })
}

/**
 * The tabbed main UI. Extracted so the auth gate above stays trivial to follow.
 */
@Composable
private fun MainScaffold(container: AppContainer, onLoggedOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTab = currentRoute in setOf(Routes.RECIPES, Routes.MENUS, Routes.PROFILE)

    Scaffold(
        bottomBar = {
            if (isTab) {
                BottomBar(navController, currentRoute)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECIPES,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.RECIPES) {
                val vm = viewModel<RecipesViewModel>(factory = viewModelFactory {
                    initializer { RecipesViewModel(container.recipeRepository, container.orderCartStore) }
                })
                RecipesScreen(
                    viewModel = vm,
                    onOpenOrder = { navController.navigate(Routes.ORDER) },
                    onNewRecipe = { navController.navigate(Routes.NEW_RECIPE) },
                    onEditRecipe = { recipeId -> navController.navigate(recipeEditRoute(recipeId)) }
                )
            }
            composable(Routes.MENUS) {
                val vm = viewModel<MenusViewModel>(factory = viewModelFactory {
                    initializer {
                        MenusViewModel(container.menuRepository, container.materialRepository)
                    }
                })
                MenusScreen(
                    viewModel = vm,
                    onOpenMaterials = { navController.navigate(Routes.MATERIALS) }
                )
            }
            composable(Routes.MATERIALS) {
                val vm = viewModel<MaterialsViewModel>(factory = viewModelFactory {
                    initializer { MaterialsViewModel(container.materialRepository) }
                })
                MaterialsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                val vm = viewModel<ProfileViewModel>(factory = viewModelFactory {
                    initializer { ProfileViewModel(container.profileRepository) }
                })
                ProfileScreen(
                    viewModel = vm,
                    onLogout = {
                        container.tokenStore.clear()
                        onLoggedOut()
                    },
                    onManageCategories = { navController.navigate(Routes.CATEGORIES) }
                )
            }
            composable(Routes.CATEGORIES) {
                val vm = viewModel<CategoriesViewModel>(factory = viewModelFactory {
                    initializer { CategoriesViewModel(container.recipeRepository) }
                })
                CategoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.ORDER) {
                val vm = viewModel<OrderViewModel>(factory = viewModelFactory {
                    initializer {
                        OrderViewModel(
                            container.recipeRepository,
                            container.menuRepository,
                            container.orderCartStore,
                            container.materialRepository
                        )
                    }
                })
                OrderScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onConfirmed = {
                        // Discard the order page cleanly, then go to 菜单. Keep these
                        // options plain: no popUpTo-with-saveState games here.
                        navController.popBackStack(Routes.RECIPES, inclusive = false)
                        navController.navigate(Routes.MENUS) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.NEW_RECIPE) {
                val vm = viewModel<RecipeEditorViewModel>(factory = viewModelFactory {
                    initializer { RecipeEditorViewModel(container.recipeRepository, container.imageUploader) }
                })
                RecipeEditorScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.RECIPE_EDIT,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: 0L
                val vm = viewModel<RecipeEditorViewModel>(factory = viewModelFactory {
                    initializer {
                        RecipeEditorViewModel(container.recipeRepository, container.imageUploader, recipeId)
                    }
                })
                RecipeEditorScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        Triple(Routes.RECIPES, "菜谱", Icons.Filled.RestaurantMenu),
        Triple(Routes.MENUS, "菜单", Icons.Filled.ReceiptLong),
        Triple(Routes.PROFILE, "个人", Icons.Filled.Person)
    )
    NavigationBar {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            // Deliberately NO restoreState here. With restoreState=true,
                            // after visiting a non-tab page (点菜/新建菜谱) once, tapping the
                            // 菜谱 tab would "restore" a previously saved stack segment
                            // instead of navigating — the tab appeared completely dead.
                            // Plain popUpTo + launchSingleTop keeps the semantics simple and
                            // predictable: every tab switch lands on that tab's root page.
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
