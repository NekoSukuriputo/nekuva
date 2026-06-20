package org.nekosukuriputo.nekuva.widget.shelf

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.core.ui.theme.NekuvaTheme
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import androidx.compose.runtime.LaunchedEffect
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.all_favourites
import nekuva.composeapp.generated.resources.favourites_categories

/**
 * Lets the user pick which favourite category the Shelf widget shows (Doki ShelfWidgetConfigActivity),
 * launched on widget placement (APPWIDGET_CONFIGURE). Persists the choice per appWidgetId, then triggers
 * the widget to render and returns RESULT_OK.
 */
class ShelfWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out before choosing, the widget is not added (Doki / Android contract).
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val favouritesRepository = GlobalContext.get().get<FavouritesRepository>()

        setContent {
            NekuvaTheme {
                var categories by remember { mutableStateOf<List<FavouriteCategory>>(emptyList()) }
                LaunchedEffect(Unit) {
                    categories = runCatching { favouritesRepository.observeCategories().first() }.getOrDefault(emptyList())
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = stringResource(Res.string.favourites_categories),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        item {
                            ConfigRow(stringResource(Res.string.all_favourites)) {
                                confirm(appWidgetId, ShelfWidgetConfig.ALL_FAVOURITES)
                            }
                        }
                        items(categories) { category ->
                            ConfigRow(category.title) { confirm(appWidgetId, category.id) }
                        }
                    }
                }
            }
        }
    }

    private fun confirm(appWidgetId: Int, categoryId: Long) {
        ShelfWidgetConfig.setCategory(this, appWidgetId, categoryId)
        // Trigger the provider's onUpdate so the widget renders with the chosen category.
        sendBroadcast(
            Intent(this, ShelfWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            },
        )
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun ConfigRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
