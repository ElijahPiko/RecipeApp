package com.recipe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.recipe.app.ui.AppNavigation
import com.recipe.app.ui.theme.RecipeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecipeTheme {
                val application = application as RecipeApplication
                AppNavigation(application.container)
            }
        }
    }
}
