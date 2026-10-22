package com.recipe.core.model

/**
 * One ingredient (a required food material or a seasoning) with its name and amount.
 * The amount is kept as a String so unit-less entries like "适量" are supported.
 */
data class Ingredient(
    val name: String,
    val amount: String
)

/**
 * A category. A recipe must belong to exactly one category.
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#FF9800",
    val sortOrder: Int = 0
)

/**
 * A cooked dish / food recipe.
 */
data class Recipe(
    val id: Long = 0,
    val name: String,
    val imageUrl: String = "",
    /**
     * Whether the backend stores an actual image (BLOB) for this recipe (as opposed to a URL).
     * When true, the image is streamed from the server endpoint referenced by [imageUrl].
     */
    val hasImage: Boolean = false,
    val category: String,
    val foodMaterials: List<Ingredient> = emptyList(),
    val seasonings: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * An item that has been "ordered" (added to the current order cart).
 */
data class OrderItem(
    val recipeId: Long,
    val recipeName: String,
    val quantity: Int = 1,
    val imageUrl: String = ""
)

/**
 * A finalized menu order record shown on the 菜单 page. The cooking cost is editable.
 */
data class MenuRecord(
    val id: Long = 0,
    val items: List<OrderItem> = emptyList(),
    val totalCost: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * The current ad-hoc order cart (the dishes the user has tapped "+" on, not yet finalized).
 */
data class OrderCart(
    val items: List<OrderItem> = emptyList()
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalQuantity: Int get() = items.sumOf { it.quantity }

    fun add(recipe: Recipe, quantity: Int = 1): OrderCart {
        val existing = items.find { it.recipeId == recipe.id }
        return if (existing == null) {
            copy(items = items + OrderItem(recipe.id, recipe.name, quantity, recipe.imageUrl))
        } else {
            copy(
                items = items.map {
                    if (it.recipeId == recipe.id) it.copy(quantity = it.quantity + quantity) else it
                }
            )
        }
    }

    fun remove(recipeId: Long): OrderCart = copy(items = items.filterNot { it.recipeId == recipeId })

    fun updateQuantity(recipeId: Long, quantity: Int): OrderCart {
        if (quantity <= 0) return remove(recipeId)
        return copy(items = items.map { if (it.recipeId == recipeId) it.copy(quantity = quantity) else it })
    }

    fun clear(): OrderCart = copy(items = emptyList())
}

/**
 * The signed-in user's public info. Never carries the password hash or token.
 */
data class AuthUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = ""
)

/**
 * The response payload of register / login: the session token plus the user.
 * The client stores [token] and sends it as `Authorization: Bearer <token>`.
 */
data class AuthResponse(
    val token: String = "",
    val user: AuthUser = AuthUser()
)

/**
 * Personal configuration for the 个人 page.
 */
data class UserProfile(
    val id: Long = 1,
    val nickname: String = "美食家",
    val avatarUrl: String = "",
    val signature: String = "记录每一道好菜",
    val location: String = "",
    val favoriteCategory: String = ""
)

/**
 * One purchase line in the 材料账单: what was bought, how much it weighed, what it cost.
 *
 * Weight is always kilograms and money is always yuan, so the derived unit price is
 * 元/千克 — the single unit the whole ledger is normalised to for now.
 */
data class MaterialBill(
    val id: Long = 0,
    val name: String,
    val weightKg: Double = 0.0,
    val price: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Derived unit price, never stored on the bill itself. */
    val pricePerKg: Double get() = if (weightKg <= 0.0) 0.0 else price / weightKg
}

/**
 * Historical average purchase price of one material, maintained per user by the server
 * in its own table. It is a weighted average: total spend / total weight, in 元/千克.
 */
data class MaterialPrice(
    val name: String = "",
    val avgPricePerKg: Double = 0.0,
    val totalWeightKg: Double = 0.0,
    val totalPrice: Double = 0.0,
    val billCount: Int = 0
)

/**
 * Totals over the whole 材料账单. [totalPrice] is what the 菜单 page shows as 「总金额」.
 */
data class MaterialStats(
    val totalPrice: Double = 0.0,
    val entryCount: Int = 0
)
