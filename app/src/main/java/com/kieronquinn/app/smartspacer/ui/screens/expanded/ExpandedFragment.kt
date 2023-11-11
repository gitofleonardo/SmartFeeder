package com.kieronquinn.app.smartspacer.ui.screens.expanded

import android.app.Activity
import android.app.KeyguardManager
import android.app.KeyguardManager.KeyguardDismissCallback
import android.app.PendingIntent
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.kieronquinn.app.smartspacer.BuildConfig
import com.kieronquinn.app.smartspacer.R
import com.kieronquinn.app.smartspacer.components.blur.BlurProvider
import com.kieronquinn.app.smartspacer.components.smartspace.ExpandedSmartspacerSession.Item
import com.kieronquinn.app.smartspacer.databinding.FragmentExpandedBinding
import com.kieronquinn.app.smartspacer.databinding.SmartspaceExpandedLongPressPopupBinding
import com.kieronquinn.app.smartspacer.databinding.SmartspaceExpandedLongPressPopupCustomWidgetBinding
import com.kieronquinn.app.smartspacer.databinding.SmartspaceExpandedLongPressPopupWidgetBinding
import com.kieronquinn.app.smartspacer.model.appshortcuts.AppShortcut
import com.kieronquinn.app.smartspacer.model.doodle.DoodleImage
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository.CustomExpandedAppWidgetConfig
import com.kieronquinn.app.smartspacer.repositories.SearchRepository.SearchApp
import com.kieronquinn.app.smartspacer.repositories.SmartspacerSettingsRepository
import com.kieronquinn.app.smartspacer.repositories.WallpaperRepository
import com.kieronquinn.app.smartspacer.sdk.client.utils.getAttrColor
import com.kieronquinn.app.smartspacer.sdk.client.views.base.SmartspacerBasePageView.SmartspaceTargetInteractionListener
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction.Companion.KEY_EXTRA_ABOUT_INTENT
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction.Companion.KEY_EXTRA_FEEDBACK_INTENT
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.expanded.ExpandedState.Shortcuts.Shortcut
import com.kieronquinn.app.smartspacer.sdk.utils.getParcelableCompat
import com.kieronquinn.app.smartspacer.sdk.utils.shouldExcludeFromSmartspacer
import com.kieronquinn.app.smartspacer.ui.activities.ExpandedActivity
import com.kieronquinn.app.smartspacer.ui.activities.MainActivity
import com.kieronquinn.app.smartspacer.ui.activities.OverlayTrampolineActivity
import com.kieronquinn.app.smartspacer.ui.base.BoundFragment
import com.kieronquinn.app.smartspacer.ui.screens.expanded.BaseExpandedAdapter.ExpandedAdapterListener
import com.kieronquinn.app.smartspacer.ui.screens.expanded.ExpandedViewModel.State
import com.kieronquinn.app.smartspacer.utils.extensions.awaitPost
import com.kieronquinn.app.smartspacer.utils.extensions.dip
import com.kieronquinn.app.smartspacer.utils.extensions.getContrastColor
import com.kieronquinn.app.smartspacer.utils.extensions.getParcelableExtraCompat
import com.kieronquinn.app.smartspacer.utils.extensions.isActivityCompat
import com.kieronquinn.app.smartspacer.utils.extensions.isDarkMode
import com.kieronquinn.app.smartspacer.utils.extensions.onApplyInsets
import com.kieronquinn.app.smartspacer.utils.extensions.onClicked
import com.kieronquinn.app.smartspacer.utils.extensions.whenCreated
import com.kieronquinn.app.smartspacer.utils.extensions.whenResumed
import com.kieronquinn.monetcompat.extensions.views.applyMonet
import com.kieronquinn.monetcompat.extensions.views.overrideRippleColor
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.hypot
import kotlin.math.min
import com.kieronquinn.app.smartspacer.sdk.client.R as SDKR

class ExpandedFragment: BoundFragment<FragmentExpandedBinding>(
    FragmentExpandedBinding::inflate
), SmartspaceTargetInteractionListener, View.OnScrollChangeListener, ExpandedAdapterListener {

    companion object {
        private const val MIN_SWIPE_DELAY = 250L
        private const val EXTRA_OPEN_ACTION = "open_action"
        private const val EXTRA_OPEN_TARGET = "open_target"

        private val COMPONENT_EXPANDED = ComponentName(
            BuildConfig.APPLICATION_ID,
            "${BuildConfig.APPLICATION_ID}.ui.activities.ExportedExpandedActivity"
        )

        fun createOpenTargetIntent(targetId: String): Intent {
            val action = OpenFromOverlayAction.OpenTarget(targetId)
            return Intent().apply {
                component = COMPONENT_EXPANDED
                putExtra(EXTRA_OPEN_ACTION, action)
            }
        }

        fun createOpenTargetUriCompatibleIntent(launchIntent: Intent?): Intent? {
            if(launchIntent?.component != COMPONENT_EXPANDED) return null
            val targetId = launchIntent.getParcelableExtraCompat(
                EXTRA_OPEN_ACTION, OpenFromOverlayAction.OpenTarget::class.java
            )?.id ?: return null
            return Intent().apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    "${BuildConfig.APPLICATION_ID}.ui.activities.ExportedExpandedActivity"
                )
                putExtra(EXTRA_OPEN_TARGET, targetId)
            }
        }
    }

    private val viewModel by viewModel<ExpandedViewModel>()
    private val wallpaperRepository by inject<WallpaperRepository>()
    private val blurProvider by inject<BlurProvider>()
    private val settingsRepository by inject<SmartspacerSettingsRepository>()
    private val adapterUpdateBus = MutableStateFlow<Long?>(null)
    private var lastSwipe: Long? = null
    private var popup: Balloon? = null
    private var topInset = 0
    private val glide by lazy { Glide.with(requireContext()) }

    private val isOverlay by lazy {
        ExpandedActivity.isOverlay(requireActivity() as ExpandedActivity)
    }

    private val sessionId by lazy {
        if(isOverlay){
            "overlay"
        }else{
            "expanded"
        }
    }

    private val widgetBindResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onWidgetBindResult(
            widgetConfigureResult,
            it.resultCode == Activity.RESULT_OK
        )
    }

    private val widgetConfigureResult = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.onWidgetConfigureResult(it.resultCode == Activity.RESULT_OK)
    }

    private val isDark = runBlocking {
        wallpaperRepository.homescreenWallpaperDarkTextColour.first()
    }

    private val adapter by lazy {
        ExpandedAdapter(
            binding.expandedRecyclerView,
            isDark,
            sessionId,
            this,
            this
        )
    }

    private val keyguardManager by lazy {
        requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private val itemTouchHelper by lazy {
        ItemTouchHelper(ExpandedRearrangeItemTouchHelperCallback(viewModel, adapter))
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val theme = if(isDark){
            R.style.Theme_Smartspacer_Wallpaper_Dark
        }else{
            R.style.Theme_Smartspacer_Wallpaper_Light
        }
        val contextThemeWrapper = ContextThemeWrapper(requireContext(), theme)
        return inflater.cloneInContext(contextThemeWrapper)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowCompat.getInsetsController(requireActivity().window, view).run {
            isAppearanceLightNavigationBars = isDark
            isAppearanceLightStatusBars = isDark
        }
        setupLoading()
        setupState()
        setupMonet()
        setupInsets()
        setupRecyclerView()
        setupUnlock()
        setupOverlaySwipe()
        setupDisabledButton()
        setupClose()
        setupAddWidget()
        handleLaunchActionIfNeeded()
        viewModel.setup(isOverlay)
    }

    private fun setupAddWidget() {
        binding.expandedSearchBox.expandedFooterButton.setOnClickListener {
            onAddWidgetClicked()
        }
    }

    override fun onDestroyView() {
        binding.expandedRecyclerView.adapter = null
        super.onDestroyView()
    }

    private fun setupLoading() = whenCreated {
        with(binding.expandedLoading) {
            (drawable as AnimatedVectorDrawable).start()
        }
    }

    private fun setupMonet() {
        binding.expandedUnlockContainer.backgroundTintList = ColorStateList.valueOf(
            monet.getBackgroundColor(requireContext())
        )
        binding.expandedUnlock.overrideRippleColor(monet.getAccentColor(requireContext()))
        binding.expandedUnlock.iconTint = ColorStateList.valueOf(
            monet.getAccentColor(requireContext())
        )
        binding.expandedDisabledButton.applyMonet()
        binding.expandedPermission.backgroundTintList = ColorStateList.valueOf(
            monet.getBackgroundColor(requireContext())
        )
    }

    private fun setupInsets() = with(binding) {
        expandedUnlockContainer.onApplyInsets { view, insets ->
            view.updatePadding(
                bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            )
        }
        root.onApplyInsets { view, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            viewModel.setTopInset(topInset)
            view.updatePadding(
                top = topInset
            )
        }
        val lockedPadding = resources.getDimensionPixelSize(R.dimen.expanded_button_unlock_height)
        expandedRecyclerView.onApplyInsets { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                .bottom + lockedPadding
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val leftInset = cutoutInsets.left
            val rightInset = cutoutInsets.right
            view.updatePadding(
                left = leftInset,
                right = rightInset,
                bottom = bottomInset
            )
        }
    }

    private fun setupRecyclerView() = with(binding.expandedRecyclerView) {
        layoutManager = createSpanLayoutManager()
        adapter = this@ExpandedFragment.adapter
        itemAnimator = PulseControlledItemAnimator()
        itemTouchHelper.attachToRecyclerView(this)
        setOnScrollChangeListener(this@ExpandedFragment)
    }

    private fun createSpanLayoutManager(): LayoutManager {
        val maxSpan = 4
        val layoutManager = GridLayoutManager(requireContext(), maxSpan)
        val lookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val item = this@ExpandedFragment.adapter.items[position]
                if (item !is Item.Widget) {
                    return maxSpan
                }
                val config = item.config ?: return maxSpan
                return min(config.spanX, maxSpan)
            }
        }
        layoutManager.spanSizeLookup = lookup
        return layoutManager
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        setBlurEnabled(true)
    }

    override fun onPause() {
        setBlurEnabled(false)
        super.onPause()
        viewModel.onPause()
    }

    private fun setBlurEnabled(enabled: Boolean) {
        if(isOverlay) return //Handled progressively by overlay
        if(settingsRepository.expandedBlurBackground.getSync()) {
            val ratio = if(enabled) 1f else 0f
            blurProvider.applyBlurToWindow(requireActivity().window, ratio)
        }else{
            val alpha = if(enabled) 128 else 0
            val backgroundColour = ColorUtils.setAlphaComponent(Color.BLACK, alpha)
            binding.root.setBackgroundColor(backgroundColour)
        }
    }

    private fun setupState() {
        handleState(viewModel.state.value)
        whenResumed {
            viewModel.state.collect {
                handleState(it)
            }
        }
    }

    private fun handleState(state: State) {
        when(state){
            is State.Loading -> {
                binding.expandedLoading.isVisible = true
                binding.expandedNestedScroll.isVisible = false
                binding.expandedUnlockContainer.isVisible = false
                binding.expandedDisabled.isVisible = false
                binding.expandedPermission.isVisible = false
            }
            is State.Disabled -> {
                binding.expandedLoading.isVisible = false
                binding.expandedNestedScroll.isVisible = false
                binding.expandedUnlockContainer.isVisible = false
                binding.expandedDisabled.isVisible = true
                binding.expandedPermission.isVisible = false
            }
            is State.PermissionRequired -> {
                binding.expandedLoading.isVisible = false
                binding.expandedNestedScroll.isVisible = false
                binding.expandedUnlockContainer.isVisible = false
                binding.expandedDisabled.isVisible = false
                binding.expandedPermission.isVisible = true
            }
            is State.Loaded -> {
                binding.expandedLoading.isVisible = false
                binding.expandedNestedScroll.isVisible = true
                binding.expandedUnlockContainer.isVisible = state.isLocked && !isOverlay
                binding.expandedDisabled.isVisible = false
                binding.expandedPermission.isVisible = false
                setStatusBarLight(state.lightStatusIcons)
                adapter.submitList(state.items)
                setup(state.searchItem)
            }
        }
    }

    fun setup(item: Item.Search) = with(binding.expandedSearchBox) {
        expandedDoodle.isVisible = item.doodleImage != null
        expandedSearchBox.root.isVisible = item.searchApp != null
        setupSearch(item)
        setupDoodle(item, root)
    }

    private fun setupSearch(item: Item.Search) = with(binding.expandedSearchBox.expandedSearchBox) {
        val searchApp = item.searchApp ?: return@with
        expandedSearchBoxMic.isVisible = searchApp.showLensAndMic
        expandedSearchBoxLens.isVisible = searchApp.showLensAndMic
        expandedSearchBoxSearch.isInvisible = searchApp.showLensAndMic
        if (searchApp.icon != null) {
            expandedSearchBoxIcon.setImageDrawable(searchApp.icon)
        } else {
            expandedSearchBoxIcon.setImageResource(R.drawable.ic_search)
        }
        if (searchApp.shouldTint) {
            expandedSearchBoxIcon.imageTintList =
                ColorStateList.valueOf(monet.getAccentColor(requireContext()))
        } else {
            expandedSearchBoxIcon.imageTintList = null
        }
        val iconPadding = if (searchApp.icon == null || searchApp.showLensAndMic) {
            requireContext().resources.getDimensionPixelSize(R.dimen.margin_6)
        } else 0
        expandedSearchBoxIcon.updatePadding(iconPadding, iconPadding, iconPadding, iconPadding)
        whenResumed {
            root.onClicked().collect {
                onSearchBoxClicked(searchApp)
            }
        }
        whenResumed {
            expandedSearchBoxLens.onClicked().collect {
                onSearchLensClicked(searchApp)
            }
        }
        whenResumed {
            expandedSearchBoxMic.onClicked().collect {
                onSearchMicClicked(searchApp)
            }
        }
    }

    private fun setupDoodle(
        item: Item.Search, root: View
    ) = with(binding.expandedSearchBox.expandedDoodle) {
        val doodle = item.doodleImage
        if(doodle != null) {
            val url = if (context.isDarkMode) {
                doodle.darkUrl ?: doodle.url
            } else doodle.url
            try {
                glide.load(url).into(this)
            } catch (e: Exception) {
                //No-op
            }
            setPadding(context.dip(doodle.padding))
            contentDescription = doodle.altText
        }
        if(item.searchBackgroundColor != null){
            root.setBackgroundResource(R.drawable.background_expanded_header_solid)
            root.backgroundTintList = ColorStateList.valueOf(item.searchBackgroundColor)
        }else{
            root.setBackgroundResource(R.drawable.background_expanded_header)
            root.backgroundTintList = null
        }
        whenResumed {
            onClicked().collect {
                onDoodleClicked(doodle ?: return@collect)
            }
        }
    }

    private fun setStatusBarLight(enabled: Boolean) {
        WindowCompat.getInsetsController(requireActivity().window, requireView())
            .isAppearanceLightStatusBars = enabled
    }

    private fun setupUnlock() = with(binding.expandedUnlock) {
        viewLifecycleOwner.whenResumed {
            onClicked().collect {
                unlockAndLaunch(null)
            }
        }
    }

    private fun setupOverlaySwipe() = viewLifecycleOwner.whenResumed {
        viewModel.overlayDrag.collect {
            lastSwipe = System.currentTimeMillis()
            popup?.dismiss()
            popup = null
        }
    }

    private fun setupDisabledButton() = with(binding.expandedDisabledButton) {
        viewLifecycleOwner.whenResumed {
            onClicked().collect {
                Intent(requireContext(), MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("smartspacer://expanded")
                    putExtra(MainActivity.EXTRA_SKIP_SPLASH, true)
                }.also { intent ->
                    startActivity(intent)
                }
            }
        }
    }

    private fun setupClose() = viewLifecycleOwner.whenResumed {
        viewModel.exitBus.collect {
            if(it && !isOverlay) {
                requireActivity().finishAndRemoveTask()
            }
        }
    }

    private fun handleLaunchActionIfNeeded() = whenResumed {
        val action = getAndClearOverlayAction()
            ?: getAndClearOverlayTarget() ?: return@whenResumed
        //Await an adapter update if needed
        adapterUpdateBus.first {
            adapter.items.isNotEmpty()
        }
        binding.expandedRecyclerView.awaitPost()
        binding.expandedNestedScroll.scrollTo(0, action.scrollPosition)
        when(action){
            is OpenFromOverlayAction.ConfigureWidget -> {
                onConfigureWidgetClicked(action.info, action.id, action.config)
            }
            is OpenFromOverlayAction.AddWidget -> {
                onAddWidgetClicked()
            }
            is OpenFromOverlayAction.Rearrange -> {
                viewModel.onRearrangeClicked()
            }
            is OpenFromOverlayAction.OpenTarget -> {
                val itemPosition = adapter.items.indexOfFirst {
                    it is Item.Target && it.target.smartspaceTargetId == action.id
                }
                if(itemPosition < 0) return@whenResumed
                val itemView = binding.expandedRecyclerView
                    .findViewHolderForAdapterPosition(itemPosition)?.itemView
                    ?: return@whenResumed
                val scrollTo = (itemView.top - topInset).coerceAtLeast(0)
                binding.expandedNestedScroll.scrollTo(0, scrollTo)
            }
            is OpenFromOverlayAction.Options -> {
                viewModel.onOptionsClicked(action.appWidgetId)
            }
        }
    }

    override fun onSearchLensClicked(searchApp: SearchApp) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("google://lens")
            component = ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.search.lens.LensExportedActivity"
            )
            putExtra("LensHomescreenShortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        unlockAndLaunch(intent)
    }

    override fun onSearchMicClicked(searchApp: SearchApp) {
        val block = {
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        if(searchApp.requiresUnlock){
            unlockAndInvoke(block)
        }else{
            block()
        }
    }

    override fun onDoodleClicked(doodleImage: DoodleImage) {
        unlockAndInvoke {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(doodleImage.searchUrl ?: return@apply)
            })
        }
    }

    override fun onSearchBoxClicked(searchApp: SearchApp) {
        unlockAndInvoke {
            startActivity(searchApp.launchIntent)
        }
    }

    private fun unlockAndLaunch(intent: Intent?) {
        unlockAndInvoke {
            try {
                startActivity(intent ?: return@unlockAndInvoke)
            }catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    SDKR.string.smartspace_long_press_popup_failed_to_launch,
                    Toast.LENGTH_LONG
                ).show()
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
            object: KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    block()
                }
            }
        )
    }

    override fun onInteraction(target: SmartspaceTarget, actionId: String?) {
        viewModel.onTargetInteraction(target, actionId)
    }

    override fun onLongPress(target: SmartspaceTarget) {
        val canDismiss = target.canBeDismissed &&
                target.featureType != SmartspaceTarget.FEATURE_WEATHER
        val aboutIntent = target.baseAction?.extras
            ?.getParcelableCompat(KEY_EXTRA_ABOUT_INTENT, Intent::class.java)
            ?.takeIf { !it.shouldExcludeFromSmartspacer() }
        val feedbackIntent = target.baseAction?.extras
            ?.getParcelableCompat(KEY_EXTRA_FEEDBACK_INTENT, Intent::class.java)
            ?.takeIf { !it.shouldExcludeFromSmartspacer() }
        if(!canDismiss && aboutIntent == null && feedbackIntent == null) return
        val position = adapter.items.indexOfFirst { item ->
            item is Item.Target && item.target == target
        }
        if(position == -1) return
        val holder = binding.expandedRecyclerView.findViewHolderForAdapterPosition(position)
            ?: return
        showPopup(holder.itemView, target, canDismiss, aboutIntent, feedbackIntent)
    }

    override fun launch(unlock: Boolean, block: () -> Unit) {
        if(unlock){
            unlockAndInvoke(block)
        }else block()
    }

    override fun onConfigureWidgetClicked(
        info: AppWidgetProviderInfo,
        id: String?,
        config: CustomExpandedAppWidgetConfig?
    ) {
        if(isOverlay){
            launchOverlayAction(OpenFromOverlayAction.ConfigureWidget(info, id, config, getScroll()))
        }else{
            unlockAndInvoke {
                viewModel.onConfigureWidgetClicked(
                    widgetBindResult,
                    widgetConfigureResult,
                    info,
                    id,
                    config
                )
            }
        }
    }

    override fun onAddWidgetClicked() {
        if(isOverlay){
            launchOverlayAction(OpenFromOverlayAction.AddWidget(getScroll()))
        }else{
            unlockAndInvoke {
                viewModel.onAddWidgetClicked()
            }
        }
    }

    override fun onShortcutClicked(shortcut: Shortcut) {
        if(shortcut.pendingIntent?.isActivityCompat() == true){
            viewModel.onShortcutClicked(requireContext(), shortcut)
        }else{
            unlockAndInvoke {
                viewModel.onShortcutClicked(requireContext(), shortcut)
            }
        }
    }

    override fun onAppShortcutClicked(appShortcut: AppShortcut) {
        unlockAndInvoke {
            viewModel.onAppShortcutClicked(appShortcut)
        }
    }

    override fun onWidgetLongClicked(viewHolder: RecyclerView.ViewHolder, appWidgetId: Int?) {
        if(appWidgetId == null) return
        val view = viewHolder.itemView
        lastSwipe?.let {
            if(System.currentTimeMillis() - it < MIN_SWIPE_DELAY){
                return //Likely a swipe
            }
        }
        val popupView = SmartspaceExpandedLongPressPopupWidgetBinding.inflate(layoutInflater)
        val background = requireContext().getAttrColor(android.R.attr.colorBackground)
        val textColour = background.getContrastColor()
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, 0)
        val popup = Balloon.Builder(requireContext())
            .setLayout(popupView)
            .setHeight(BalloonSizeSpec.WRAP)
            .setWidthResource(SDKR.dimen.smartspace_long_press_popup_width)
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
            .setBackgroundColor(background)
            .setArrowColor(background)
            .setArrowSize(10)
            .setArrowPosition(0.5f)
            .setCornerRadius(16f)
            .setBalloonAnimation(BalloonAnimation.FADE)
            .build()
        popup.showAlignBottom(view)
        popupView.expandedLongPressPopupReset.setTextColor(textColour)
        popupView.expandedLongPressPopupReset.iconTint = ColorStateList.valueOf(textColour)
        popupView.expandedLongPressPopupReset.setOnClickListener {
            popup.dismiss()
            unlockAndInvoke {
                viewModel.onAppWidgetReset(appWidgetId)
            }
        }
        this.popup = popup
        startDrag(viewHolder)
    }

    override fun onWidgetDeleteClicked(widget: Item.RemovedWidget) {
        viewModel.onDeleteCustomWidget(widget.appWidgetId ?: return)
    }

    private fun showPopup(
        view: View,
        target: SmartspaceTarget,
        canDismiss: Boolean,
        aboutIntent: Intent?,
        feedbackIntent: Intent?
    ) {
        lastSwipe?.let {
            if(System.currentTimeMillis() - it < MIN_SWIPE_DELAY){
                return //Likely a swipe
            }
        }
        val popupView = SmartspaceExpandedLongPressPopupBinding.inflate(layoutInflater)
        val background = requireContext().getAttrColor(android.R.attr.colorBackground)
        val textColour = background.getContrastColor()
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, 0)
        val popup = Balloon.Builder(requireContext())
            .setLayout(popupView)
            .setHeight(BalloonSizeSpec.WRAP)
            .setWidthResource(SDKR.dimen.smartspace_long_press_popup_width)
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
            .setBackgroundColor(background)
            .setArrowColor(background)
            .setArrowSize(10)
            .setArrowPosition(0.5f)
            .setCornerRadius(16f)
            .setBalloonAnimation(BalloonAnimation.FADE)
            .build()
        popup.showAlignBottom(view)
        popupView.smartspaceLongPressPopupAbout.isVisible = aboutIntent != null
        popupView.smartspaceLongPressPopupAbout.setTextColor(textColour)
        popupView.smartspaceLongPressPopupAbout.iconTint = ColorStateList.valueOf(textColour)
        popupView.smartspaceLongPressPopupAbout.setOnClickListener {
            popup.dismiss()
            unlockAndLaunch(aboutIntent)
        }
        popupView.smartspaceLongPressPopupFeedback.isVisible = feedbackIntent != null
        popupView.smartspaceLongPressPopupFeedback.setTextColor(textColour)
        popupView.smartspaceLongPressPopupFeedback.iconTint = ColorStateList.valueOf(textColour)
        popupView.smartspaceLongPressPopupFeedback.setOnClickListener {
            popup.dismiss()
            unlockAndLaunch(feedbackIntent)
        }
        popupView.smartspaceLongPressPopupDismiss.isVisible = canDismiss
        popupView.smartspaceLongPressPopupDismiss.setTextColor(textColour)
        popupView.smartspaceLongPressPopupDismiss.iconTint = ColorStateList.valueOf(textColour)
        popupView.smartspaceLongPressPopupDismiss.setOnClickListener {
            popup.dismiss()
            viewModel.onTargetDismiss(target)
        }
        this.popup = popup
    }

    override fun onCustomWidgetLongClicked(viewHolder: ViewHolder, view: View, widget: Item.Widget) {
        lastSwipe?.let {
            if(System.currentTimeMillis() - it < MIN_SWIPE_DELAY){
                return //Likely a swipe
            }
        }
        val popupView = SmartspaceExpandedLongPressPopupCustomWidgetBinding.inflate(layoutInflater)
        val background = requireContext().getAttrColor(android.R.attr.colorBackground)
        val textColour = background.getContrastColor()
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, 0)
        val popup = Balloon.Builder(requireContext())
            .setLayout(popupView)
            .setHeight(BalloonSizeSpec.WRAP)
            .setWidthResource(SDKR.dimen.smartspace_long_press_popup_width)
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
            .setBackgroundColor(background)
            .setArrowColor(background)
            .setArrowSize(10)
            .setArrowPosition(0.5f)
            .setCornerRadius(16f)
            .setBalloonAnimation(BalloonAnimation.FADE)
            .build()
        popup.showAlignBottom(view)
        popupView.expandedLongPressPopupDelete.setTextColor(textColour)
        popupView.expandedLongPressPopupDelete.iconTint = ColorStateList.valueOf(textColour)
        popupView.expandedLongPressPopupDelete.setOnClickListener {
            popup.dismiss()
            unlockAndInvoke {
                viewModel.onDeleteCustomWidget(
                    widget.appWidgetId ?: return@unlockAndInvoke
                )
            }
        }
        popupView.expandedLongPressPopupOptions.setTextColor(textColour)
        popupView.expandedLongPressPopupOptions.iconTint = ColorStateList.valueOf(textColour)
        popupView.expandedLongPressPopupOptions.setOnClickListener {
            popup.dismiss()
            val appWidgetId = widget.appWidgetId ?: return@setOnClickListener
            if(isOverlay){
                launchOverlayAction(OpenFromOverlayAction.Options(getScroll(), appWidgetId))
            }else{
                unlockAndInvoke {
                    viewModel.onOptionsClicked(appWidgetId)
                }
            }
        }
        popupView.expandedLongPressPopupRearrange.setTextColor(textColour)
        popupView.expandedLongPressPopupRearrange.iconTint = ColorStateList.valueOf(textColour)
        popupView.expandedLongPressPopupRearrange.setOnClickListener {
            popup.dismiss()
            if(isOverlay){
                val appWidgetId = widget.appWidgetId ?: return@setOnClickListener
                launchOverlayAction(OpenFromOverlayAction.Rearrange(appWidgetId))
            }else {
                unlockAndInvoke {
                    viewModel.onRearrangeClicked()
                }
            }
        }
        this.popup = popup
        startDrag(viewHolder)
    }

    private fun startDrag(viewHolder: ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
        PredragCondition(viewHolder) {
            popup?.dismiss()
        }
    }

    override fun onScrollChange(
        view: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int
    ) {
        lastSwipe = System.currentTimeMillis()
    }

    override fun shouldTrampolineLaunches(): Boolean = isOverlay

    override fun trampolineLaunch(pendingIntent: PendingIntent) {
        OverlayTrampolineActivity.trampoline(requireContext(), pendingIntent)
    }

    private fun getAndClearOverlayAction(): OpenFromOverlayAction? {
        return requireActivity().intent.run {
            getParcelableExtraCompat(EXTRA_OPEN_ACTION, OpenFromOverlayAction::class.java).also {
                removeExtra(EXTRA_OPEN_ACTION)
            }
        }
    }

    private fun getAndClearOverlayTarget(): OpenFromOverlayAction.OpenTarget? {
        return requireActivity().intent.run {
            getStringExtra(EXTRA_OPEN_TARGET).also {
                removeExtra(EXTRA_OPEN_TARGET)
            }?.let {
                OpenFromOverlayAction.OpenTarget(it)
            }
        }
    }

    private fun launchOverlayAction(action: OpenFromOverlayAction) {
        val intent = ExpandedActivity.createExportedOverlayIntent(requireContext()).apply {
            putExtra(EXTRA_OPEN_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        unlockAndLaunch(intent)
    }

    private fun getScroll() = with(binding.expandedNestedScroll) {
        scrollY
    }

    sealed class OpenFromOverlayAction(open val scrollPosition: Int): Parcelable {
        @Parcelize
        data class OpenTarget(val id: String): OpenFromOverlayAction(0)
        @Parcelize
        data class ConfigureWidget(
            val info: AppWidgetProviderInfo,
            val id: String?,
            val config: CustomExpandedAppWidgetConfig?,
            override val scrollPosition: Int
        ): OpenFromOverlayAction(scrollPosition)
        @Parcelize
        data class AddWidget(override val scrollPosition: Int):
            OpenFromOverlayAction(scrollPosition)
        @Parcelize
        data class Rearrange(override val scrollPosition: Int):
            OpenFromOverlayAction(scrollPosition)
        @Parcelize
        data class Options(
            override val scrollPosition: Int, val appWidgetId: Int
        ): OpenFromOverlayAction(scrollPosition)
    }

    private class PulseControlledItemAnimator: DefaultItemAnimator() {

        override fun animateChange(
            oldHolder: ViewHolder,
            newHolder: ViewHolder,
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int
        ): Boolean {
            val isMove = fromX != toX || fromY != toY
            if(!isMove && shouldOverride(oldHolder, newHolder)) {
                dispatchChangeStarting(oldHolder, true)
                dispatchChangeStarting(newHolder, false)
                oldHolder.itemView.alpha = 0f
                newHolder.itemView.alpha = 1f
                dispatchChangeFinished(oldHolder, true)
                dispatchChangeFinished(newHolder, false)
                return true
            }
            return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
        }

        private fun shouldOverride(oldHolder: ViewHolder, newHolder: ViewHolder): Boolean {
            if(oldHolder !is BaseExpandedAdapter.ViewHolder.Target) return false
            if(newHolder !is BaseExpandedAdapter.ViewHolder.Target) return false
            return oldHolder.adapterPosition == newHolder.adapterPosition
        }

    }

    private class PredragCondition(val viewHolder: ViewHolder, val onEndCallback: () -> Unit): OnDrawListener {

        val startTranX: Float = viewHolder.itemView.translationX
        val startTranY: Float = viewHolder.itemView.translationY

        val threshold: Float = viewHolder.itemView.context.resources
            .getDimensionPixelSize(R.dimen.start_drag_threshold).toFloat()

        init {
            viewHolder.itemView.viewTreeObserver.addOnDrawListener(this)
        }

        override fun onDraw() {
            val nowTranX = viewHolder.itemView.translationX
            val nowTranY = viewHolder.itemView.translationY
            val dist = hypot(nowTranX - startTranX, nowTranY - startTranY)
            if (dist >= threshold) {
                onEndCallback.invoke()
                viewHolder.itemView.post {
                    viewHolder.itemView.viewTreeObserver.removeOnDrawListener(this)
                }
            }
        }
    }
}