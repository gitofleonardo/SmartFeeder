package com.kieronquinn.app.smartspacer.ui.screens.expanded.proxy

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.kieronquinn.app.smartspacer.databinding.FragmentExpandedProxyBinding
import com.kieronquinn.app.smartspacer.ui.base.BoundFragment
import com.kieronquinn.app.smartspacer.ui.screens.expanded.ExpandedFragment
import com.kieronquinn.app.smartspacer.utils.extensions.getParcelableExtraCompat
import com.kieronquinn.app.smartspacer.utils.extensions.whenResumed
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExpandedProxyFragment : BoundFragment<FragmentExpandedProxyBinding>(FragmentExpandedProxyBinding::inflate) {
    companion object {
        const val EXTRA_OPEN_ACTION = "open_action"
        const val EXTRA_OPEN_TARGET = "open_target"
    }

    private val keyguardManager by lazy {
        requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private val viewModel by viewModel<ExpandedProxyViewModel>()

    private val widgetConfigureResult = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        requireActivity().finish()
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.background = ColorDrawable(Color.TRANSPARENT)
        handleLaunchActionIfNeeded()
    }

    private fun handleLaunchActionIfNeeded() = whenResumed {
        val action = getAndClearOverlayAction()
            ?: getAndClearOverlayTarget() ?: return@whenResumed
        when(action){
            is ExpandedFragment.OpenFromOverlayAction.AddWidget -> {
                onAddWidgetClicked()
            }
            is ExpandedFragment.OpenFromOverlayAction.Options -> {
                viewModel.onOptionsClicked(action.appWidgetId)
            }
            is ExpandedFragment.OpenFromOverlayAction.ConfigureWidget -> {
                viewModel.onConfigureWidgetClicked(
                    widgetConfigureResult,
                    action.info,
                    action.id,
                    action.config,
                    action.appWidgetId
                )
            }
            else -> { requireActivity().finish() }
        }
    }

    private fun onAddWidgetClicked() {
        unlockAndInvoke {
            viewModel.onAddWidgetClicked()
        }
    }

    private fun getAndClearOverlayTarget(): ExpandedFragment.OpenFromOverlayAction.OpenTarget? {
        return requireActivity().intent.run {
            getStringExtra(EXTRA_OPEN_TARGET).also {
                removeExtra(EXTRA_OPEN_TARGET)
            }?.let {
                ExpandedFragment.OpenFromOverlayAction.OpenTarget(it)
            }
        }
    }

    private fun getAndClearOverlayAction(): ExpandedFragment.OpenFromOverlayAction? {
        return requireActivity().intent.run {
            getParcelableExtraCompat(EXTRA_OPEN_ACTION, ExpandedFragment.OpenFromOverlayAction::class.java).also {
                removeExtra(EXTRA_OPEN_ACTION)
            }
        }
    }

    private fun unlockAndInvoke(block: () -> Unit) {
        if(!keyguardManager.isKeyguardLocked){
            block()
            return
        }
        keyguardManager.requestDismissKeyguard(
            requireActivity(),
            object: KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    block()
                }
            }
        )
    }
}