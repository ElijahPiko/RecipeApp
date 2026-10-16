package com.recipe.app

import android.app.Application
import com.recipe.app.data.AppContainer

/**
 * Holds the application-level composition root.
 */
class RecipeApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(BuildConfig.API_BASE_URL)
    }
}
