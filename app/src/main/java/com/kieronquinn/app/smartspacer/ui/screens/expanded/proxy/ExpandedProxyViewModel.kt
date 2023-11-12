package com.kieronquinn.app.smartspacer.ui.screens.expanded.proxy

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.smartspacer.components.navigation.ExpandedProxyNavigation
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository
import com.kieronquinn.app.smartspacer.utils.extensions.allowBackground
import kotlinx.coroutines.launch

abstract class ExpandedProxyViewModel: ViewModel() {
    abstract fun onAddWidgetClicked()
    abstract fun onOptionsClicked(appWidgetId: Int)
    abstract fun onConfigureWidgetClicked(
        configureLauncher: ActivityResultLauncher<IntentSenderRequest>,
        info: AppWidgetProviderInfo,
        id: String?,
        config: ExpandedRepository.CustomExpandedAppWidgetConfig?,
        appWidgetId: Int?
    )
}

class ExpandedProxyViewModelImpl(
    context: Context,
    private val navigation: ExpandedProxyNavigation,
    private val expandedRepository: ExpandedRepository,
): ExpandedProxyViewModel() {
    override fun onAddWidgetClicked() {
        viewModelScope.launch {
            navigation.navigate(ExpandedProxyFragmentDirections.actionProxyFragmentToExpandedAddWidgetBottomSheetFragment())
        }
    }

    override fun onOptionsClicked(appWidgetId: Int) {
        viewModelScope.launch {
            navigation.navigate(ExpandedProxyFragmentDirections.actionProxyFragmentToExpandedWidgetOptionsBottomSheetFragment(appWidgetId))
        }
    }

    override fun onConfigureWidgetClicked(
        configureLauncher: ActivityResultLauncher<IntentSenderRequest>,
        info: AppWidgetProviderInfo,
        id: String?,
        config: ExpandedRepository.CustomExpandedAppWidgetConfig?,
        appWidgetId: Int?
    ) {
        if (info.configure == null) {
            return
        }
        appWidgetId?.let {
            expandedRepository.createConfigIntentSender(it).also { sender ->
                configureLauncher.launch(
                    IntentSenderRequest.Builder(sender).build(),
                    ActivityOptionsCompat.makeBasic().allowBackground()
                )
            }
        }
    }
}