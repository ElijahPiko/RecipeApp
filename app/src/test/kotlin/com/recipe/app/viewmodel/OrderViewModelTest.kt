package com.recipe.app.viewmodel

import com.recipe.app.data.OrderCartStore
import com.recipe.core.data.fake.InMemoryMaterialRepository
import com.recipe.core.data.fake.InMemoryMenuRepository
import com.recipe.core.data.fake.InMemoryRecipeRepository
import com.recipe.core.model.Ingredient
import com.recipe.core.model.MaterialBill
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
        val vm = OrderViewModel(recipeRepo, InMemoryMenuRepository(), cart, InMemoryMaterialRepository())
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
        val vm = OrderViewModel(recipeRepo, menuRepo, cart, InMemoryMaterialRepository())
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmed)
        assertTrue(cart.cart.value.isEmpty)
        assertEquals(1, menuRepo.stats().orderCount)
    }

    @Test
    fun `theoretical price uses average price times the labelled weight`() = runTest(dispatcher) {
        val recipeRepo = InMemoryRecipeRepository()
        val materials = InMemoryMaterialRepository()
        // 排骨 40 元/千克
        materials.add(MaterialBill(name = "排骨", weightKg = 1.0, price = 40.0))

        val recipe = Recipe(
            id = 3,
            name = "糖醋排骨",
            category = "家常菜",
            foodMaterials = listOf(Ingredient("排骨", "500克"), Ingredient("葱", "2根"))
        )
        recipeRepo.save(recipe)
        val cart = OrderCartStore()
        cart.add(recipe)
        cart.add(recipe) // 数量 2

        val vm = OrderViewModel(recipeRepo, InMemoryMenuRepository(), cart, materials)
        advanceUntilIdle()

        // 0.5 千克 × 40 元/千克 = 20 元/份；葱没有均价，不参与计算
        assertEquals(20.0, vm.uiState.value.estimates[3]!!.unitPrice, 0.0001)
        assertEquals(listOf("葱"), vm.uiState.value.estimates[3]!!.unpricedMaterials)
        // 总价按数量累计：20 × 2
        assertEquals(40.0, vm.uiState.value.estimatedTotal, 0.0001)
    }

    @Test
    fun `order count on the menu page grows by one and the estimate is snapshotted`() = runTest(dispatcher) {
        val recipeRepo = InMemoryRecipeRepository()
        val menuRepo = InMemoryMenuRepository()
        val materials = InMemoryMaterialRepository()
        // 0.5 千克花了 40 元 → 均价 80 元/千克；账单合计 40 元
        materials.add(MaterialBill(name = "排骨", weightKg = 0.5, price = 40.0))
        val recipe = Recipe(id = 4, name = "排骨汤", category = "汤羹",
            foodMaterials = listOf(Ingredient("排骨", "1千克")))
        recipeRepo.save(recipe)

        val cart = OrderCartStore()
        cart.add(recipe)
        val orderVm = OrderViewModel(recipeRepo, menuRepo, cart, materials)
        advanceUntilIdle()
        orderVm.confirm()
        advanceUntilIdle()

        // 保存后菜单页的点菜次数 +1，理论价随记录一起留档（1kg × 80 = 80）
        val menusVm = MenusViewModel(menuRepo, materials)
        menusVm.refresh()
        advanceUntilIdle()
        assertEquals(1, menusVm.uiState.value.stats.orderCount)
        assertEquals(80.0, menuRepo.list().first().estimatedCost, 0.0001)
        // 理论价不影响菜单页「总金额」：那里只认材料账单的 40 元
        assertEquals(40.0, menusVm.uiState.value.totalSpend, 0.0001)
    }
}
