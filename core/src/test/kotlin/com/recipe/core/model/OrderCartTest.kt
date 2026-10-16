package com.recipe.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderCartTest {

    private val recipe = Recipe(id = 7, name = "红烧肉", category = "家常菜")

    @Test
    fun `adding a recipe creates an item`() {
        val cart = OrderCart().add(recipe)
        assertEquals(1, cart.items.size)
        assertEquals("红烧肉", cart.items.first().recipeName)
        assertEquals(1, cart.items.first().quantity)
    }

    @Test
    fun `adding the same recipe increments quantity`() {
        val cart = OrderCart().add(recipe).add(recipe)
        assertEquals(1, cart.items.size)
        assertEquals(2, cart.items.first().quantity)
        assertEquals(2, cart.totalQuantity)
    }

    @Test
    fun `removing a recipe removes the item`() {
        val cart = OrderCart().add(recipe).remove(7)
        assertTrue(cart.isEmpty)
    }

    @Test
    fun `updating quantity below one removes item`() {
        val cart = OrderCart().add(recipe).updateQuantity(7, 0)
        assertTrue(cart.isEmpty)
    }

    @Test
    fun `clear empties the cart`() {
        val cart = OrderCart().add(recipe).add(Recipe(id = 8, name = "鱼", category = "家常菜")).clear()
        assertTrue(cart.isEmpty)
        assertEquals(0, cart.totalQuantity)
    }
}
