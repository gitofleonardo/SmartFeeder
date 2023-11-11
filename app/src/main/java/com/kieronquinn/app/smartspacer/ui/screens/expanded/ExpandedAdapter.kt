package com.kieronquinn.app.smartspacer.ui.screens.expanded

import android.appwidget.AppWidgetProviderInfo
import android.content.res.ColorStateList
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.kieronquinn.app.smartspacer.R
import com.kieronquinn.app.smartspacer.components.smartspace.ExpandedSmartspacerSession.Item
import com.kieronquinn.app.smartspacer.components.smartspace.ExpandedSmartspacerSession.Item.Type
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedComplicationsBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedFooterBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedRemoteviewsBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedRemovedWidgetBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedSearchBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedShortcutsBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedStatusBarSpaceBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedTargetBinding
import com.kieronquinn.app.smartspacer.databinding.ItemExpandedWidgetBinding
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository.CustomExpandedAppWidgetConfig
import com.kieronquinn.app.smartspacer.sdk.client.views.base.SmartspacerBasePageView.SmartspaceTargetInteractionListener
import com.kieronquinn.app.smartspacer.ui.screens.expanded.BaseExpandedAdapter.ExpandedAdapterListener
import com.kieronquinn.app.smartspacer.ui.screens.expanded.BaseExpandedAdapter.ViewHolder
import com.kieronquinn.app.smartspacer.ui.views.LifecycleAwareRecyclerView
import com.kieronquinn.app.smartspacer.utils.extensions.apply
import com.kieronquinn.app.smartspacer.utils.extensions.dip
import com.kieronquinn.app.smartspacer.utils.extensions.isDarkMode
import com.kieronquinn.app.smartspacer.utils.extensions.onClicked
import com.kieronquinn.app.smartspacer.utils.extensions.whenResumed
import com.kieronquinn.app.smartspacer.utils.remoteviews.WidgetContextWrapper
import com.kieronquinn.monetcompat.core.MonetCompat
import org.koin.core.component.inject
import java.util.Collections

class ExpandedAdapter(
    recyclerView: RecyclerView,
    isDark: Boolean,
    private val sessionId: String,
    private val expandedAdapterListener: ExpandedAdapterListener,
    private val targetInteractionListener: SmartspaceTargetInteractionListener
): LifecycleAwareRecyclerView.Adapter<ViewHolder>(recyclerView), BaseExpandedAdapter {

    private val theme = if(isDark) {
        R.style.Theme_Smartspacer_Wallpaper_Dark
    } else {
        R.style.Theme_Smartspacer_Wallpaper_Light
    }

    private val context = ContextThemeWrapper(recyclerView.context.applicationContext, theme)
    private val layoutInflater = LayoutInflater.from(context)
    private val widgetContext = WidgetContextWrapper(context)
    private val monet = MonetCompat.getInstance()
    private val glide = Glide.with(context)

    override val isRearrange = false
    override val expandedRepository by inject<ExpandedRepository>()

    var items: List<Item> = emptyList()

    override fun getItemViewType(position: Int): Int {
        return items[position].type.ordinal
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(Type.values()[viewType]){
            Type.STATUS_BAR_SPACE -> ViewHolder.StatusBarSpace(
                ItemExpandedStatusBarSpaceBinding.inflate(layoutInflater, parent, false)
            )
            Type.SEARCH -> ViewHolder.Search(
                ItemExpandedSearchBinding.inflate(layoutInflater, parent, false)
            )
            Type.TARGET -> ViewHolder.Target(
                ItemExpandedTargetBinding.inflate(layoutInflater, parent, false)
            )
            Type.COMPLICATIONS -> ViewHolder.Complications(
                ItemExpandedComplicationsBinding.inflate(layoutInflater, parent, false)
            )
            Type.REMOTE_VIEWS -> ViewHolder.RemoteViews(
                ItemExpandedRemoteviewsBinding.inflate(layoutInflater, parent, false)
            )
            Type.WIDGET -> ViewHolder.Widget(
                ItemExpandedWidgetBinding.inflate(layoutInflater, parent, false)
            )
            Type.REMOVED_WIDGET -> ViewHolder.RemovedWidget(
                ItemExpandedRemovedWidgetBinding.inflate(layoutInflater, parent, false)
            )
            Type.SHORTCUTS -> ViewHolder.Shortcuts(
                ItemExpandedShortcutsBinding.inflate(layoutInflater, parent, false)
            )
            Type.FOOTER -> ViewHolder.Footer(
                ItemExpandedFooterBinding.inflate(layoutInflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        when(holder){
            is ViewHolder.StatusBarSpace -> holder.setup(item as Item.StatusBarSpace)
            is ViewHolder.Search -> holder.setup(item as Item.Search)
            is ViewHolder.Target -> holder.setup(item as Item.Target)
            is ViewHolder.Complications -> holder.setup(item as Item.Complications)
            is ViewHolder.RemoteViews -> holder.setup(item as Item.RemoteViews)
            is ViewHolder.Widget -> {
                holder.setup(item as Item.Widget, sessionId, targetInteractionListener)
            }
            is ViewHolder.RemovedWidget -> holder.setup(item as Item.RemovedWidget)
            is ViewHolder.Shortcuts -> holder.setup(item as Item.Shortcuts)
            is ViewHolder.Footer -> holder.setup(item as Item.Footer)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        when(holder) {
            is ViewHolder.Widget -> holder.destroy()
            else -> {
                //No-op by default
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        onDetached(sessionId)
    }

    private fun ViewHolder.StatusBarSpace.setup(item: Item.StatusBarSpace) = with(binding) {
        root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = item.topInset
        }
    }

    private fun ViewHolder.Search.setup(item: Item.Search) = with(binding) {
        expandedDoodle.isVisible = item.doodleImage != null
        expandedSearchBox.root.isVisible = item.searchApp != null
        root.updatePadding(top = item.topInset)
        setupSearch(item)
        setupDoodle(item, root)
    }

    private fun ViewHolder.Search.setupSearch(item: Item.Search) = with(binding.expandedSearchBox) {
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
                ColorStateList.valueOf(monet.getAccentColor(context))
        } else {
            expandedSearchBoxIcon.imageTintList = null
        }
        val iconPadding = if (searchApp.icon == null || searchApp.showLensAndMic) {
            context.resources.getDimensionPixelSize(R.dimen.margin_6)
        } else 0
        expandedSearchBoxIcon.updatePadding(iconPadding, iconPadding, iconPadding, iconPadding)
        whenResumed {
            root.onClicked().collect {
                expandedAdapterListener.onSearchBoxClicked(searchApp)
            }
        }
        whenResumed {
            expandedSearchBoxLens.onClicked().collect {
                expandedAdapterListener.onSearchLensClicked(searchApp)
            }
        }
        whenResumed {
            expandedSearchBoxMic.onClicked().collect {
                expandedAdapterListener.onSearchMicClicked(searchApp)
            }
        }
    }

    private fun ViewHolder.Search.setupDoodle(
        item: Item.Search, root: View
    ) = with(binding.expandedDoodle) {
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
                expandedAdapterListener.onDoodleClicked(doodle ?: return@collect)
            }
        }
    }

    private fun ViewHolder.Target.setup(target: Item.Target) = with(binding) {
        val tintColour = getTintColour(target.isDark)
        itemExpandedTargetTarget.setTarget(target.target, targetInteractionListener, tintColour)
    }

    @Synchronized
    private fun ViewHolder.Complications.setup(complications: Item.Complications) = with(binding) {
        val tintColour = getTintColour(complications.isDark)
        root.run {
            layoutManager = FlexboxLayoutManager(context, FlexDirection.ROW).apply {
                justifyContent = JustifyContent.CENTER
            }
            isNestedScrollingEnabled = false
            adapter = ExpandedComplicationAdapter(
                context,
                complications.complications.complications,
                tintColour,
                targetInteractionListener
            )
        }
    }

    private fun ViewHolder.RemoteViews.setup(remoteViews: Item.RemoteViews) = with(binding) {
        itemExpandedRemoteviewsContainer.run {
            removeAllViews()
            try {
                remoteViews.remoteViews.apply(
                    widgetContext,
                    this,
                    targetInteractionListener
                ).also {
                    addView(it)
                }
            }catch (e: Exception){
                Log.e("ExpandedState", "Error adding RemoteViews", e)
            }
        }
    }

    private fun ViewHolder.Shortcuts.setup(shortcuts: Item.Shortcuts) = with(binding) {
        val tintColour = getTintColour(shortcuts.isDark)
        whenResumed {
            root.run {
                layoutManager = FlexboxLayoutManager(context, FlexDirection.ROW).apply {
                    justifyContent = JustifyContent.CENTER
                }
                isNestedScrollingEnabled = false
                adapter = ExpandedShortcutAdapter(
                    this,
                    shortcuts.shortcuts,
                    tintColour,
                    expandedAdapterListener::onAppShortcutClicked,
                    expandedAdapterListener::onShortcutClicked
                )
            }
        }
    }

    private fun ViewHolder.Footer.setup(footer: Item.Footer) = with(binding) {
        val tintColour = getTintColour(footer.isDark)
        expandedFooterButton.isVisible = footer.hasClickedAdd
        expandedFooterButton.setTextColor(tintColour)
        expandedFooterButton.iconTint = ColorStateList.valueOf(tintColour)
        expandedFooterButtonWide.isVisible = !footer.hasClickedAdd
        expandedFooterButtonWide.setTextColor(tintColour)
        expandedFooterButtonWide.iconTint = ColorStateList.valueOf(tintColour)
        whenResumed {
            expandedFooterButton.onClicked().collect {
                expandedAdapterListener.onAddWidgetClicked()
            }
        }
        whenResumed {
            expandedFooterButtonWide.onClicked().collect {
                expandedAdapterListener.onAddWidgetClicked()
            }
        }
    }

    override fun onCustomWidgetLongClicked(viewHolder: RecyclerView.ViewHolder, view: View, widget: Item.Widget) {
        expandedAdapterListener.onCustomWidgetLongClicked(viewHolder, view, widget)
    }

    override fun onWidgetLongClicked(viewHolder: ViewHolder, appWidgetId: Int?) {
        expandedAdapterListener.onWidgetLongClicked(viewHolder, appWidgetId)
    }

    override fun onDeleteWidgetClicked(widget: Item.RemovedWidget) {
        expandedAdapterListener.onWidgetDeleteClicked(widget)
    }

    override fun onConfigureWidgetClicked(
        provider: AppWidgetProviderInfo,
        id: String?,
        config: CustomExpandedAppWidgetConfig?
    ) {
        expandedAdapterListener.onConfigureWidgetClicked(provider, id, config)
    }

    fun moveItem(indexFrom: Int, indexTo: Int): Boolean {
        if (indexFrom < indexTo) {
            for (i in indexFrom until indexTo) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in indexFrom downTo indexTo + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(indexFrom, indexTo)
        return true
    }

    fun submitList(items: List<Item>) {
        val diffResult = DiffUtil.calculateDiff(DiffCallback(this.items, items))
        diffResult.dispatchUpdatesTo(this)
        this.items = items
    }

    private class DiffCallback(val oldList: List<Item>, val newList: List<Item>): DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].getStaticId() == newList[newItemPosition].getStaticId()
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return when {
                oldItem is Item.Target && newItem is Item.Target -> {
                    oldItem.target.equalsForUi(newItem.target)
                }
                oldItem is Item.Shortcuts && newItem is Item.Shortcuts -> {
                    oldItem.shortcuts == newItem.shortcuts
                }
                else -> oldItem == newItem
            }
        }
    }
}