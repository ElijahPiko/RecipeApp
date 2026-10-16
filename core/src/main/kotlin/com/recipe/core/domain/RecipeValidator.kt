package com.recipe.core.domain

import com.recipe.core.model.Recipe

/**
 * Domain validation for recipes. Ensures the required fields are present before persisting.
 */
object RecipeValidator {

    fun validate(recipe: Recipe): List<String> {
        val errors = mutableListOf<String>()
        if (recipe.name.isBlank()) errors += "菜名不能为空"
        if (recipe.category.isBlank()) errors += "必须指定一个分类"
        if (recipe.foodMaterials.isEmpty()) errors += "至少需要填写一种食品材料"
        if (recipe.foodMaterials.any { it.name.isBlank() }) errors += "食品材料名称不能为空"
        if (recipe.steps.isEmpty()) errors += "至少需要填写一个操作步骤"
        if (recipe.steps.any { it.isBlank() }) errors += "操作步骤不能为空"
        return errors
    }

    fun isValid(recipe: Recipe): Boolean = validate(recipe).isEmpty()
}
