package com.recipe.core.domain

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe

/**
 * "一键输出所需材料": merges the food materials and seasonings of a set of recipes
 * so the user can see everything they need to buy / prepare in one place.
 */
data class AggregatedIngredients(
    val foodMaterials: List<Ingredient>,
    val seasonings: List<Ingredient>
)

object IngredientsAggregator {

    /**
     * Aggregates materials across [recipes]. Ingredients sharing the same name within a group
     * are combined: numeric amounts are summed, otherwise amounts are joined and de-duplicated.
     */
    fun aggregate(recipes: List<Recipe>): AggregatedIngredients {
        fun merge(input: List<Ingredient>): List<Ingredient> =
            input
                .filter { it.name.isNotBlank() }
                .groupBy { it.name.trim() }
                .map { (name, list) ->
                    Ingredient(name = name, amount = combineAmounts(list.map { it.amount }))
                }
                .sortedBy { it.name }

        return AggregatedIngredients(
            foodMaterials = merge(recipes.flatMap { it.foodMaterials }),
            seasonings = merge(recipes.flatMap { it.seasonings })
        )
    }

    private fun combineAmounts(amounts: List<String>): String {
        val cleaned = amounts.filter { it.isNotBlank() }.map { it.trim() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size == 1) return cleaned.first()

        val numeric = cleaned.mapNotNull { it.toDoubleOrNull() }
        if (numeric.size == cleaned.size) {
            val sum = numeric.sum()
            return if (sum == sum.toLong().toDouble()) sum.toLong().toString() else "%.2f".format(sum)
        }
        // Amounts such as "2个" and "3个" carry a unit; if all share the same unit, sum the numbers.
        val numberUnit = cleaned.map { parseNumberUnit(it) }
        if (numberUnit.all { it != null } && numberUnit.map { it!!.unit }.distinct().size == 1) {
            val unit = numberUnit.first()!!.unit
            val sum = numberUnit.sumOf { it!!.value }
            val sumStr = if (sum == sum.toLong().toDouble()) sum.toLong().toString() else "%.2f".format(sum)
            return sumStr + unit
        }
        return cleaned.distinct().joinToString(" + ")
    }

    private data class NumberUnit(val value: Double, val unit: String)

    private fun parseNumberUnit(value: String): NumberUnit? {
        val match = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(.*?)\\s*$").find(value) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        return NumberUnit(number, match.groupValues[2])
    }
}
