package com.recipe.app.viewmodel

import com.recipe.app.data.OrderCartStore
import com.recipe.core.data.fake.InMemoryMenuRepository
import com.recipe.core.data.fake.InMemoryRecipeRepository
import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `aggregates food materials and seasonings`() = runTest(dispatcher) {
        val recipeRepo = InMemoryRecipeRepository()
        val recipe = Recipe(
            id = 1,
            name = "蛋炒饭",
            category = "主食",
            foodMaterials = listOf(Ingredient("米饭", "1碗"), Ingredient("鸡蛋", "2个")),
            seasonings = listOf(Ingredient("盐", "1勺"))
        )
        recipeRepo.save(recipe)
        val cart = OrderCartStore()
        cart.add(recipe)
        val vm = OrderViewModel(recipeRepo, InMemoryMenuRepository(), cart)
        advanceUntilIdle()
        val aggregated = vm.uiState.value.aggregated
        assertEquals(listOf(Ingredient("米饭", "1碗"), Ingredient("鸡蛋", "2个")), aggregated.foodMaterials)
        assertEquals(listOf(Ingredient("盐", "1勺")), aggregated.seasonings)
    }

    @Test
    fun `confirm creates a menu record and clears the cart`() = runTest(dispatcher) {
        val recipeRepo = InMemoryRecipeRepository()
        val menuRepo = InMemoryMenuRepository()
        val recipe = Recipe(id = 2, name = "清蒸鱼", category = "家常菜",
            foodMaterials = listOf(Ingredient("鱼", "1条")), steps = listOf("蒸"))
        recipeRepo.save(recipe)
        val cart = OrderCartStore()
        cart.add(recipe)
        val vm = OrderViewModel(recipeRepo, menuRepo, cart)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmed)
        assertTrue(cart.cart.value.isEmpty)
        assertEquals(1, menuRepo.stats().orderCount)
    }
}
