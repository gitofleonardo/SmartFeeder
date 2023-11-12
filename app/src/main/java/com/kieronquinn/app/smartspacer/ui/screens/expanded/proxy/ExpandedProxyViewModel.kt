package com.kieronquinn.app.smartspacer.ui.screens.expanded.proxy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.smartspacer.components.navigation.ExpandedProxyNavigation
import kotlinx.coroutines.launch

abstract class ExpandedProxyViewModel: ViewModel() {
    abstract fun onAddWidgetClicked()
    abstract fun onOptionsClicked(appWidgetId: Int)
}

class ExpandedProxyViewModelImpl(
    context: Context,
    private val navigation: ExpandedProxyNavigation
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
}