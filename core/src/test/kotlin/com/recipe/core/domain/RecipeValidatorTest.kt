package com.recipe.core.domain

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeValidatorTest {

    private val valid = Recipe(
        name = "番茄炒蛋",
        category = "家常菜",
        foodMaterials = listOf(Ingredient("番茄", "2个")),
        steps = listOf("炒")
    )

    @Test
    fun `valid recipe passes`() {
        assertTrue(RecipeValidator.isValid(valid))
        assertTrue(RecipeValidator.validate(valid).isEmpty())
    }

    @Test
    fun `blank name is rejected`() {
        assertFalse(RecipeValidator.isValid(valid.copy(name = "  ")))
        assertTrue(RecipeValidator.validate(valid.copy(name = "")).any { it.contains("菜名") })
    }

    @Test
    fun `missing category is rejected`() {
        assertFalse(RecipeValidator.isValid(valid.copy(category = "")))
        assertTrue(RecipeValidator.validate(valid.copy(category = " ")).any { it.contains("分类") })
    }

    @Test
    fun `missing food materials is rejected`() {
        assertFalse(RecipeValidator.isValid(valid.copy(foodMaterials = emptyList())))
    }

    @Test
    fun `blank food material name is rejected`() {
        val result = RecipeValidator.validate(valid.copy(foodMaterials = listOf(Ingredient(" ", "1勺"))))
        assertTrue(result.any { it.contains("食品材料") })
    }

    @Test
    fun `missing steps is rejected`() {
        assertFalse(RecipeValidator.isValid(valid.copy(steps = emptyList())))
    }

    @Test
    fun `blank step is rejected`() {
        val result = RecipeValidator.validate(valid.copy(steps = listOf("", "翻锅")))
        assertTrue(result.any { it.contains("操作步骤") })
    }

    @Test
    fun `seasonings and optional fields are not required`() {
        assertTrue(RecipeValidator.isValid(valid.copy(seasonings = emptyList(), imageUrl = "")))
    }

    @Test
    fun `collects all errors together`() {
        val errors = RecipeValidator.validate(Recipe(name = "", category = "", foodMaterials = emptyList(), steps = emptyList()))
        assertEquals(4, errors.size)
    }
}
