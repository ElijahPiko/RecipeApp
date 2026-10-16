package com.recipe.core.model

/**
 * Built-in set of categories. The "新建菜谱" form must pick one of these (or a custom one).
 */
object CategoryCatalog {
    val defaults: List<Category> = listOf(
        Category(1, "家常菜", "#FF7043", 0),
        Category(2, "汤羹", "#29B6F6", 1),
        Category(3, "凉菜", "#66BB6A", 2),
        Category(4, "主食", "#FFA726", 3),
        Category(5, "烘焙", "#AB47BC", 4),
        Category(6, "饮品", "#26C6DA", 5),
        Category(7, "其他", "#90A4AE", 6)
    )

    val defaultNames: List<String>
        get() = defaults.map { it.name }
}
