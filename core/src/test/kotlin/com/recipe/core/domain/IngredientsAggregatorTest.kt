package com.recipe.core.domain

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientsAggregatorTest {

    private fun recipe(name: String, food: List<Ingredient>, seasonings: List<Ingredient> = emptyList()) =
        Recipe(id = name.hashCode().toLong(), name = name, category = "家常菜", foodMaterials = food, seasonings = seasonings)

    @Test
    fun `empty recipes produce empty aggregation`() {
        val result = IngredientsAggregator.aggregate(emptyList())
        assertEquals(emptyList<Ingredient>(), result.foodMaterials)
        assertEquals(emptyList<Ingredient>(), result.seasonings)
    }

    @Test
    fun `sums numeric amounts for duplicate names`() {
        val result = IngredientsAggregator.aggregate(
            listOf(
                recipe("甲", listOf(Ingredient("鸡蛋", "2个"))),
                recipe("乙", listOf(Ingredient("鸡蛋", "3个")))
            )
        )
        assertEquals(listOf(Ingredient("鸡蛋", "5个")), result.foodMaterials)
    }

    @Test
    fun `non numeric amounts are joined`() {
        val result = IngredientsAggregator.aggregate(
            listOf(
                recipe("甲", listOf(Ingredient("盐", "适量"))),
                recipe("乙", listOf(Ingredient("盐", "少许")))
            )
        )
        assertEquals(listOf(Ingredient("盐", "适量 + 少许")), result.foodMaterials)
    }

    @Test
    fun `sums identical amounts with a shared unit`() {
        val result = IngredientsAggregator.aggregate(
            listOf(
                recipe("甲", listOf(Ingredient("糖", "1勺")), listOf(Ingredient("酱油", "1勺"))),
                recipe("乙", listOf(Ingredient("糖", "1勺")), listOf(Ingredient("酱油", "1勺")))
            )
        )
        assertEquals(listOf(Ingredient("糖", "2勺")), result.foodMaterials)
        assertEquals(listOf(Ingredient("酱油", "2勺")), result.seasonings)
    }

    @Test
    fun `separates food and seasonings`() {
        val result = IngredientsAggregator.aggregate(
            listOf(
                recipe("咖喱鸡", listOf(Ingredient("鸡腿", "2个"), Ingredient("土豆", "2个")), listOf(Ingredient("咖喱块", "2块")))
            )
        )
        assertEquals(listOf(Ingredient("土豆", "2个"), Ingredient("鸡腿", "2个")), result.foodMaterials)
        assertEquals(listOf(Ingredient("咖喱块", "2块")), result.seasonings)
    }

    @Test
    fun `blank names are ignored`() {
        val result = IngredientsAggregator.aggregate(
            listOf(recipe("甲", listOf(Ingredient("", "1个"), Ingredient("   ", "2个"), Ingredient("洋葱", "1个"))))
        )
        assertEquals(listOf(Ingredient("洋葱", "1个")), result.foodMaterials)
    }

    @Test
    fun `different units are joined rather than summed`() {
        val result = IngredientsAggregator.aggregate(
            listOf(
                recipe("甲", listOf(Ingredient("鸡蛋", "2个"))),
                recipe("乙", listOf(Ingredient("鸡蛋", "3只")))
            )
        )
        assertEquals(listOf(Ingredient("鸡蛋", "2个 + 3只")), result.foodMaterials)
    }
}
