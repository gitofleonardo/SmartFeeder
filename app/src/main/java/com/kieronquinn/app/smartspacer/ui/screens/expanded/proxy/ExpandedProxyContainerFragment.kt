package com.kieronquinn.app.smartspacer.ui.screens.expanded.proxy

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kieronquinn.app.smartspacer.R
import com.kieronquinn.app.smartspacer.components.navigation.ExpandedProxyNavigation
import com.kieronquinn.app.smartspacer.databinding.FragmentExpandedProxyContainerBinding
import com.kieronquinn.app.smartspacer.ui.base.BaseContainerFragment
import org.koin.android.ext.android.inject

class ExpandedProxyContainerFragment: BaseContainerFragment<FragmentExpandedProxyContainerBinding>(FragmentExpandedProxyContainerBinding::inflate) {

    override val appBar: AppBarLayout? = null
    override val bottomNavigation: BottomNavigationView? = null
    override val collapsingToolbar: CollapsingToolbarLayout? = null
    override val toolbar: Toolbar? = null
    private val _navigation by inject<ExpandedProxyNavigation>()
    private var finishOnStackChanged = false

    override val navigation by lazy {
        _navigation
    }

    override val fragment by lazy {
        binding.navHostFragmentExpanded
    }

    override val navHostFragment by lazy {
        childFragmentManager.findFragmentById(R.id.nav_host_fragment_expanded) as NavHostFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.background = ColorDrawable(Color.TRANSPARENT)
    }

    override fun onTopFragmentChanged(topFragment: Fragment, currentDestination: NavDestination) {
        super.onTopFragmentChanged(topFragment, currentDestination)
        if (finishOnStackChanged) {
            requireActivity().finish()
        }
        if (currentDestination.id != R.id.proxyFragment) {
            finishOnStackChanged = true
        }
    }
}