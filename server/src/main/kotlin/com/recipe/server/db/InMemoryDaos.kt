package com.recipe.server.db

import com.recipe.core.domain.MenuStats
import com.recipe.core.domain.MenuStatsCalculator
import com.recipe.core.model.CategoryCatalog
import com.recipe.core.model.Ingredient
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.Recipe
import com.recipe.core.model.UserProfile
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory DAOs used by unit tests and as a graceful fallback when MySQL is unreachable.
 */
class InMemoryRecipeDao : RecipeDao {
    private val store = LinkedHashMap<Long, Recipe>()
    private val images = linkedMapOf<Long, ByteArray>()
    private val ids = AtomicLong(0)

    override fun search(search: String, category: String?): List<Recipe> {
        val q = search.trim()
        val cat = category?.trim().orEmpty()
        return store.values.map { withImage(it) }.filter {
            (q.isEmpty() ||
                it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.foodMaterials.any { m -> m.name.contains(q, ignoreCase = true) } ||
                it.seasonings.any { s -> s.name.contains(q, ignoreCase = true) }) &&
                (cat.isEmpty() || it.category == cat)
        }.sortedByDescending { it.createdAt }
    }

    override fun get(id: Long): Recipe? = store[id]?.let { withImage(it) }

    override fun save(recipe: Recipe): Recipe {
        val saved = if (recipe.id == 0L) recipe.copy(id = ids.incrementAndGet()) else recipe
        store[saved.id] = saved
        return saved
    }

    override fun delete(id: Long) { store.remove(id) }

    override fun categories(): List<String> = CategoryCatalog.defaultNames

    override fun getImage(id: Long): ByteArray? = images[id]

    override fun saveImage(id: Long, bytes: ByteArray): Boolean {
        if (!store.containsKey(id)) return false
        images[id] = bytes
        return true
    }

    private fun withImage(recipe: Recipe): Recipe =
        recipe.copy(hasImage = images.containsKey(recipe.id))
}

class InMemoryMenuDao : MenuDao {
    private val store = LinkedHashMap<Long, MenuRecord>()
    private val ids = AtomicLong(1000)

    override fun list(): List<MenuRecord> = store.values.sortedByDescending { it.createdAt }
    override fun stats(): MenuStats = MenuStatsCalculator.calculate(list())
    override fun create(record: MenuRecord): MenuRecord {
        val saved = if (record.id == 0L) record.copy(id = ids.incrementAndGet()) else record
        store[saved.id] = saved
        return saved
    }
    override fun updateCost(id: Long, totalCost: Double): MenuRecord? {
        val current = store[id] ?: return null
        val updated = current.copy(totalCost = totalCost)
        store[id] = updated
        return updated
    }
    override fun delete(id: Long) { store.remove(id) }
}

class InMemoryProfileDao : ProfileDao {
    private var profile = UserProfile()
    override fun get(): UserProfile = profile
    override fun save(profile: UserProfile): UserProfile { this.profile = profile; return profile }
}
