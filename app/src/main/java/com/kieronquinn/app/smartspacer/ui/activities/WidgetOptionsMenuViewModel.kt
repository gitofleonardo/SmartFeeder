package com.kieronquinn.app.smartspacer.ui.activities

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.smartspacer.components.navigation.WidgetOptionsNavigation
import com.kieronquinn.app.smartspacer.repositories.SmartspaceRepository
import com.kieronquinn.app.smartspacer.sdk.annotations.LimitedNativeSupport
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.ui.activities.configuration.ConfigurationActivity
import com.kieronquinn.app.smartspacer.ui.activities.configuration.ConfigurationActivity.NavGraphMapping
import com.kieronquinn.app.smartspacer.ui.screens.widget.SmartspacerWidgetConfigurationFragment
import kotlinx.coroutines.launch

abstract class WidgetOptionsMenuViewModel: ViewModel() {

    abstract fun onDismissClicked(target: SmartspaceTarget)
    abstract fun onAboutClicked(target: SmartspaceTarget, errorCallback: () -> Unit)
    abstract fun onFeedbackClicked(target: SmartspaceTarget, errorCallback: () -> Unit)
    abstract fun onSettingsClicked(context: Context)
    abstract fun onConfigureClicked(context: Context, appWidgetId: Int, owner: String)

}

@OptIn(LimitedNativeSupport::class)
class WidgetOptionsMenuViewModelImpl(
    private val navigation: WidgetOptionsNavigation,
    private val smartspaceRepository: SmartspaceRepository
): WidgetOptionsMenuViewModel() {

    override fun onDismissClicked(target: SmartspaceTarget) {
        viewModelScope.launch {
            smartspaceRepository.notifyDismissEvent(target.smartspaceTargetId)
        }
    }

    override fun onAboutClicked(target: SmartspaceTarget, errorCallback: () -> Unit) {
        viewModelScope.launch {
            val intent = target.aboutIntent ?: return@launch
            navigation.navigate(intent, errorCallback)
        }
    }

    override fun onFeedbackClicked(target: SmartspaceTarget, errorCallback: () -> Unit) {
        viewModelScope.launch {
            val intent = target.feedbackIntent ?: return@launch
            navigation.navigate(intent, errorCallback)
        }
    }

    override fun onSettingsClicked(context: Context) {
        viewModelScope.launch {
            navigation.navigate(Intent(context, MainActivity::class.java))
        }
    }

    override fun onConfigureClicked(context: Context, appWidgetId: Int, owner: String) {
        viewModelScope.launch {
            val intent = ConfigurationActivity.createIntent(
                context, NavGraphMapping.WIDGET_SMARTSPACER
            ).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(SmartspacerWidgetConfigurationFragment.EXTRA_CALLING_PACKAGE, owner)
            }
            navigation.navigate(intent)
        }
    }

}