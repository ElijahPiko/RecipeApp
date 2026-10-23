package com.recipe.app.viewmodel

import com.recipe.app.data.OrderCartStore
import com.recipe.core.data.fake.InMemoryMaterialRepository
import com.recipe.core.data.fake.InMemoryMenuRepository
import com.recipe.core.data.fake.InMemoryProfileRepository
import com.recipe.core.data.fake.InMemoryRecipeRepository
import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import com.recipe.core.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecipesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads seeded recipes when refreshed`() = runTest(dispatcher) {
        val vm = RecipesViewModel(InMemoryRecipeRepository(), OrderCartStore())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.recipes.size)
    }

    @Test
    fun `add to order updates the shared cart`() = runTest(dispatcher) {
        val cart = OrderCartStore()
        val vm = RecipesViewModel(InMemoryRecipeRepository(), cart)
        vm.refresh()
        advanceUntilIdle()
        val recipe = vm.uiState.value.recipes.first()
        vm.addToOrder(recipe)
        assertEquals(1, cart.cart.value.items.size)
    }

    @Test
    fun `search filters by keyword`() = runTest(dispatcher) {
        val vm = RecipesViewModel(InMemoryRecipeRepository(), OrderCartStore())
        vm.refresh()
        advanceUntilIdle()
        vm.onSearchChange("牛腩")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.recipes.size)
    }
}

class NewRecipeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class RecordingUploader : com.recipe.app.data.ImageUploader {
        val uploaded = mutableListOf<Pair<Long, ByteArray>>()
        override suspend fun upload(recipeId: Long, bytes: ByteArray) { uploaded += recipeId to bytes }
    }

    private fun vm(uploader: RecordingUploader = RecordingUploader()) =
        NewRecipeViewModel(InMemoryRecipeRepository(), uploader)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `save with valid data assigns an id`() = runTest(dispatcher) {
        val vm = vm()
        vm.setName("可乐鸡翅")
        vm.setCategory("家常菜")
        vm.updateMaterial(0, Ingredient("鸡翅", "8个"))
        vm.updateStep(0, "腌制后煎至金黄")
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedId)
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun `save without category reports validation error`() = runTest(dispatcher) {
        val vm = vm()
        vm.setName("没有分类的菜")
        vm.updateMaterial(0, Ingredient("肉", "1斤"))
        vm.updateStep(0, "煮")
        vm.save()
        assertTrue(vm.uiState.value.errors.any { it.contains("分类") })
        assertEquals(null, vm.uiState.value.savedId)
    }

    @Test
    fun `materials and steps can be added and removed`() {
        val vm = vm()
        vm.addMaterial()
        assertEquals(2, vm.uiState.value.materials.size)
        vm.removeMaterial(1)
        assertEquals(1, vm.uiState.value.materials.size)
        vm.addStep()
        assertEquals(2, vm.uiState.value.steps.size)
    }

    @Test
    fun `save uploads the chosen image to the created recipe`() = runTest(dispatcher) {
        val uploader = RecordingUploader()
        val vm = vm(uploader)
        vm.setName("图片菜")
        vm.setCategory("家常菜")
        vm.updateMaterial(0, Ingredient("肉", "1斤"))
        vm.updateStep(0, "煮")
        vm.setImageBytes(byteArrayOf(1, 2, 3, 4))
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedId)
        assertEquals(1, uploader.uploaded.size)
        assertEquals(vm.uiState.value.savedId, uploader.uploaded.first().first)
        assertTrue(uploader.uploaded.first().second.contentEquals(byteArrayOf(1, 2, 3, 4)))
    }
}

class MenusViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads stats and records`() = runTest(dispatcher) {
        val repo = InMemoryMenuRepository()
        repo.create(com.recipe.core.model.MenuRecord(totalCost = 40.0))
        repo.create(com.recipe.core.model.MenuRecord(totalCost = 60.0))
        val vm = MenusViewModel(repo, InMemoryMaterialRepository())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.records.size)
        assertEquals(100.0, vm.uiState.value.stats.totalCost, 0.0001)
    }

    @Test
    fun `total spend comes from the material ledger not the menu costs`() = runTest(dispatcher) {
        val menuRepo = InMemoryMenuRepository()
        menuRepo.create(com.recipe.core.model.MenuRecord(totalCost = 999.0))
        val materials = InMemoryMaterialRepository()
        materials.add(com.recipe.core.model.MaterialBill(name = "排骨", weightKg = 1.0, price = 40.0))
        materials.add(com.recipe.core.model.MaterialBill(name = "青菜", weightKg = 0.5, price = 10.0))

        val vm = MenusViewModel(menuRepo, materials)
        vm.refresh()
        advanceUntilIdle()

        // 总金额 = 账单累计 50，而不是菜单记录里的 999
        assertEquals(50.0, vm.uiState.value.totalSpend, 0.0001)
        // 平均花费 = 总金额 ÷ 1 次点菜
        assertEquals(50.0, vm.uiState.value.averageSpend, 0.0001)
    }

    @Test
    fun `uploading a menu photo marks the record and clears the uploading flag`() = runTest(dispatcher) {
        val repo = InMemoryMenuRepository()
        val menu = repo.create(com.recipe.core.model.MenuRecord(items = emptyList()))
        val vm = MenusViewModel(repo, InMemoryMaterialRepository())
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.records.first().hasPhoto)

        vm.uploadPhoto(menu.id, byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.records.first().hasPhoto)
        assertEquals(null, vm.uiState.value.uploadingId)
        assertEquals(3, repo.photo(menu.id)?.size)
    }
}

class MaterialsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `adding bills updates totals and the weighted average`() = runTest(dispatcher) {
        val repo = InMemoryMaterialRepository()
        val vm = MaterialsViewModel(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.add("排骨", 1.0, 40.0)
        advanceUntilIdle()
        vm.add("排骨", 1.5, 45.0)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.bills.size)
        assertEquals(85.0, state.stats.totalPrice, 0.0001)
        // (40 + 45) / (1.0 + 1.5) = 34 元/千克
        assertEquals(34.0, state.averageFor("排骨")!!.avgPricePerKg, 0.0001)
    }

    @Test
    fun `deleting the last bill drops the material average`() = runTest(dispatcher) {
        val repo = InMemoryMaterialRepository()
        val vm = MaterialsViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        vm.add("土豆", 2.0, 8.0)
        advanceUntilIdle()

        vm.delete(vm.uiState.value.bills.first().id)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.bills.isEmpty())
        assertEquals(0.0, vm.uiState.value.stats.totalPrice, 0.0001)
        assertEquals(null, vm.uiState.value.averageFor("土豆"))
    }
}

class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads then saves profile`() = runTest(dispatcher) {
        val vm = ProfileViewModel(InMemoryProfileRepository(), InMemoryRecipeRepository())
        advanceUntilIdle()
        val updated = vm.uiState.value.profile.copy(nickname = "大厨")
        vm.update(updated)
        vm.saveProfile()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        assertEquals("大厨", vm.uiState.value.profile.nickname)
    }

    @Test
    fun `categories can be added renamed and deleted`() = runTest(dispatcher) {
        val repo = InMemoryRecipeRepository()
        val vm = ProfileViewModel(InMemoryProfileRepository(), repo)
        advanceUntilIdle()

        vm.setNewCategoryName("烧烤")
        vm.addCategory()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.categories.any { it.name == "烧烤" })

        val added = vm.uiState.value.categories.first { it.name == "烧烤" }
        vm.startEdit(added)
        vm.setEditName("烤串")
        vm.saveEditCategory()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.categories.any { it.name == "烧烤" })
        assertTrue(vm.uiState.value.categories.any { it.name == "烤串" })

        vm.deleteCategory(vm.uiState.value.categories.first { it.name == "烤串" })
        advanceUntilIdle()
        assertFalse(vm.uiState.value.categories.any { it.name == "烤串" })
    }
}
