package com.recipe.server.db

import com.recipe.core.domain.MenuStats
import com.recipe.core.domain.MenuStatsCalculator
import com.recipe.core.model.CategoryCatalog
import com.recipe.core.model.Ingredient
import com.recipe.core.model.MaterialBill
import com.recipe.core.model.MaterialPrice
import com.recipe.core.model.MaterialStats
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
    private val owners = LinkedHashMap<Long, Long>()
    private val photos = LinkedHashMap<Long, ByteArray>()
    private val photoVersions = LinkedHashMap<Long, Long>()
    private val ids = AtomicLong(1000)

    override fun list(userId: Long): List<MenuRecord> =
        store.values
            .filter { owners[it.id] == userId }
            .map {
                it.copy(
                    hasPhoto = photos.containsKey(it.id),
                    photoUpdatedAt = photoVersions[it.id] ?: 0L
                )
            }
            .sortedByDescending { it.createdAt }

    override fun stats(userId: Long): MenuStats = MenuStatsCalculator.calculate(list(userId))

    override fun create(userId: Long, record: MenuRecord): MenuRecord {
        val saved = if (record.id == 0L) record.copy(id = ids.incrementAndGet()) else record
        store[saved.id] = saved
        owners[saved.id] = userId
        return saved
    }

    override fun updateCost(userId: Long, id: Long, totalCost: Double): MenuRecord? {
        if (owners[id] != userId) return null
        val current = store[id] ?: return null
        val updated = current.copy(totalCost = totalCost)
        store[id] = updated
        return updated
    }

    override fun delete(userId: Long, id: Long) {
        if (owners[id] != userId) return
        store.remove(id)
        owners.remove(id)
        photos.remove(id)
        photoVersions.remove(id)
    }

    override fun savePhoto(userId: Long, id: Long, bytes: ByteArray): Boolean {
        if (owners[id] != userId) return false
        photos[id] = bytes
        photoVersions[id] = System.currentTimeMillis()
        return true
    }

    override fun getPhoto(id: Long): ByteArray? = photos[id]
}

class InMemoryProfileDao : ProfileDao {
    private val profiles = LinkedHashMap<Long, UserProfile>()

    override fun get(userId: Long): UserProfile = profiles[userId] ?: UserProfile(id = userId)

    override fun save(userId: Long, profile: UserProfile): UserProfile {
        val saved = profile.copy(id = userId)
        profiles[userId] = saved
        return saved
    }
}

/**
 * In-memory [MaterialDao]. Mirrors the SQL contract: averages are weighted by weight and
 * are recomputed from the surviving bills on delete.
 */
class InMemoryMaterialDao : MaterialDao {
    private val bills = LinkedHashMap<Long, MaterialBill>()
    private val owners = LinkedHashMap<Long, Long>()
    private val ids = AtomicLong(2000)

    override fun bills(userId: Long): List<MaterialBill> =
        bills.values.filter { owners[it.id] == userId }
            .sortedWith(compareByDescending<MaterialBill> { it.createdAt }.thenByDescending { it.id })

    override fun add(userId: Long, bill: MaterialBill): MaterialBill {
        val saved = bill.copy(id = ids.incrementAndGet())
        bills[saved.id] = saved
        owners[saved.id] = userId
        return saved
    }

    override fun delete(userId: Long, id: Long): Boolean {
        if (owners[id] != userId) return false
        bills.remove(id)
        owners.remove(id)
        return true
    }

    override fun prices(userId: Long): List<MaterialPrice> =
        bills(userId).groupBy { it.name }.map { (name, list) ->
            val weight = list.sumOf { it.weightKg }
            val total = list.sumOf { it.price }
            MaterialPrice(
                name = name,
                avgPricePerKg = if (weight > 0) total / weight else 0.0,
                totalWeightKg = weight,
                totalPrice = total,
                billCount = list.size
            )
        }.sortedBy { it.name }

    override fun stats(userId: Long): MaterialStats {
        val mine = bills(userId)
        return MaterialStats(totalPrice = mine.sumOf { it.price }, entryCount = mine.size)
    }
}
