package com.kieronquinn.app.smartspacer.components.smartspace

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
import android.content.pm.ParceledListSlice
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.kieronquinn.app.smartspacer.components.smartspace.ExpandedSmartspacerSession.Item
import com.kieronquinn.app.smartspacer.components.smartspace.compat.TargetMerger.Companion.BLANK_TARGET_PREFIX
import com.kieronquinn.app.smartspacer.model.appshortcuts.AppShortcut
import com.kieronquinn.app.smartspacer.model.appshortcuts.ShortcutQueryWrapper
import com.kieronquinn.app.smartspacer.model.database.ExpandedAppWidget
import com.kieronquinn.app.smartspacer.model.doodle.DoodleImage
import com.kieronquinn.app.smartspacer.model.smartspace.TargetHolder
import com.kieronquinn.app.smartspacer.repositories.DatabaseRepository
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository.CustomExpandedAppWidgetConfig
import com.kieronquinn.app.smartspacer.repositories.SearchRepository
import com.kieronquinn.app.smartspacer.repositories.SearchRepository.SearchApp
import com.kieronquinn.app.smartspacer.repositories.ShizukuServiceRepository
import com.kieronquinn.app.smartspacer.repositories.SmartspaceRepository.SmartspacePageHolder
import com.kieronquinn.app.smartspacer.repositories.SmartspacerSettingsRepository
import com.kieronquinn.app.smartspacer.repositories.SmartspacerSettingsRepository.TintColour
import com.kieronquinn.app.smartspacer.repositories.WallpaperRepository
import com.kieronquinn.app.smartspacer.repositories.WidgetRepository
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceConfig
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceSessionId
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.UiSurface
import com.kieronquinn.app.smartspacer.sdk.model.expanded.ExpandedState
import com.kieronquinn.app.smartspacer.sdk.model.expanded.ExpandedState.BaseShortcut
import com.kieronquinn.app.smartspacer.ui.screens.expanded.ExpandedViewModel.Complications.Complication
import com.kieronquinn.app.smartspacer.utils.extensions.getBackgroundColour
import com.kieronquinn.app.smartspacer.utils.extensions.isColorDark
import com.kieronquinn.app.smartspacer.utils.extensions.lockscreenShowing
import com.kieronquinn.app.smartspacer.utils.extensions.split
import com.kieronquinn.app.smartspacer.utils.extensions.whenCreated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import java.util.LinkedList
import com.kieronquinn.app.smartspacer.ui.screens.expanded.ExpandedViewModel.Complications as ExpandedComplications

@Suppress("CloseTarget")
class ExpandedSmartspacerSession(
    context: Context,
    override val sessionId: SmartspaceSessionId,
    private val collectInto: suspend (List<Item>) -> Unit
): BaseSmartspacerSession<Item, SmartspaceSessionId>(
    context, SmartspaceConfig(0, UiSurface.HOMESCREEN, ""), sessionId
) {

    companion object {
        private const val MAX_SHORTCUTS = 10
    }

    private val expandedRepository by inject<ExpandedRepository>()
    private val settingsRepository by inject<SmartspacerSettingsRepository>()
    private val databaseRepository by inject<DatabaseRepository>()
    private val searchRepository by inject<SearchRepository>()
    private val widgetRepository by inject<WidgetRepository>()
    private val wallpaperRepository by inject<WallpaperRepository>()
    private val shizukuServiceRepository by inject<ShizukuServiceRepository>()
    private val topInset = MutableStateFlow(0)
    private val resumeBus = MutableStateFlow(System.currentTimeMillis())
    private val glide = Glide.with(context)

    override val targetCount = flowOf(Integer.MAX_VALUE)

    override val uiSurface = context.lockscreenShowing().mapLatest {
        if (it) UiSurface.LOCKSCREEN else UiSurface.HOMESCREEN
    }

    private val isDarkMode = uiSurface.flatMapLatest {
        when(it) {
            UiSurface.HOMESCREEN -> wallpaperRepository.homescreenWallpaperDarkTextColour
            UiSurface.LOCKSCREEN -> wallpaperRepository.lockscreenWallpaperDarkTextColour
            UiSurface.MEDIA_DATA_MANAGER -> wallpaperRepository.homescreenWallpaperDarkTextColour
        }
    }

    private val isDark = combine(
        isDarkMode.filterNotNull(),
        settingsRepository.expandedTintColour.asFlow()
    ) { dark, mode ->
        when(mode){
            TintColour.AUTOMATIC -> dark
            TintColour.BLACK -> true
            TintColour.WHITE -> false
        }
    }

    private val expandedWidgetConfigs = expandedRepository.getExpandedAppWidgets()
        .stateIn(lifecycleScope, SharingStarted.Eagerly, null)

    private var appShortcuts: List<ShortcutInfo>? = null

    private val doodle = combine(
        settingsRepository.expandedShowDoodle.asFlow(),
        resumeBus
    ) { show, _ ->
        if(show) searchRepository.getDoodle() else null
    }

    private val searchBox = combine(
        settingsRepository.expandedShowSearchBox.asFlow(),
        searchRepository.expandedSearchApp,
        doodle,
        isDark
    ) { showSearch, app, doodle, dark ->
        if(!showSearch && doodle == null) return@combine null
        val searchApp = if(showSearch){
            app
        }else null
        val doodleBitmap = doodle?.getBitmap(dark)
        val doodleBackground = doodleBitmap?.getBackgroundColour()
        val isLightStatusBar = doodleBackground?.isColorDark()?.let { !it } ?: false
        Item.Search(doodle, searchApp, doodleBackground, isLightStatusBar, isDark = dark)
    }

    private suspend fun DoodleImage.getBitmap(dark: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val url = if(dark){
            darkUrl ?: url
        }else url
        try {
            glide.asBitmap().load(url).submit().get()
        }catch (e: Exception) {
            null
        }
    }

    private val userSettings = combine(
        settingsRepository.enhancedMode.asFlow(),
        searchBox,
        isDark,
        settings.expandedWidgetUseGoogleSans.asFlow()
    ) { enhanced, search, dark, googleSans ->
        ExpandedSettings(enhanced, search, dark, googleSans)
    }

    private val viewState = combine(
        topInset,
        settings.expandedHasClickedAdd.asFlow(),
        resumeBus
    ) { inset, hasClickedAdd, _ ->
        ExpandedViewState(inset, hasClickedAdd)
    }

    private val expandedCustomAppWidgets = combine(
        expandedRepository.expandedCustomAppWidgets,
        uiSurface,
        isDark,
        settings.expandedWidgetUseGoogleSans.asFlow()
    ) { widgets, surface, dark, useGoogleSans ->
        val providers = widgetRepository.getProviders()
        val width = expandedRepository.widgetColumnWidth
        val height = expandedRepository.widgetRowHeight
        widgets.mapNotNull { widget ->
            if(!widget.showWhenLocked && surface != UiSurface.HOMESCREEN){
                return@mapNotNull null
            }
            val provider = providers.firstOrNull { info ->
                info.provider == ComponentName.unflattenFromString(widget.provider)
            } ?: return@mapNotNull Item.RemovedWidget(widget.appWidgetId, dark)
            val widgetWidth = width * widget.spanX
            val widgetHeight = height * widget.spanY
            val config = CustomExpandedAppWidgetConfig(
                widget.spanX, widget.spanY, widget.index, widget.showWhenLocked
            )
            Item.Widget(
                provider,
                widget.appWidgetId,
                widget.id,
                widgetWidth,
                widgetHeight,
                true,
                useGoogleSans,
                config,
                dark
            )
        }
    }

    private val expandedWidgets = combine(
        expandedWidgetConfigs.filterNotNull(),
        expandedCustomAppWidgets
    ) { ids, custom ->
        Pair(ids, custom)
    }

    override suspend fun collectInto(id: SmartspaceSessionId, targets: List<Item>) {
        collectInto(targets)
    }

    override fun convert(
        pages: Flow<List<SmartspacePageHolder>>,
        uiSurface: Flow<UiSurface>
    ): Flow<List<Item>> {
        return combine(
            pages,
            uiSurface,
            expandedWidgets,
            userSettings,
            viewState
        ) { p, s, w, u, v ->
            p.toItems(s, w.first, w.second, u, v)
        }.flowOn(Dispatchers.IO)
    }

    override fun applyActionOverrides(targets: Flow<List<TargetHolder>>): Flow<List<TargetHolder>> {
        return targets //Don't override actions in expanded mode
    }

    fun setTopInset(inset: Int) = whenCreated {
        topInset.emit(inset)
    }

    override fun onResume() {
        super.onResume()
        whenCreated {
            resumeBus.emit(System.currentTimeMillis())
        }
    }

    fun onDeleteCustomWidget(appWidgetId: Int) = whenCreated {
        databaseRepository.deleteExpandedCustomAppWidget(appWidgetId)
        widgetRepository.deallocateAppWidgetId(appWidgetId)
    }

    private suspend fun List<SmartspacePageHolder>.toItems(
        surface: UiSurface,
        widgets: List<ExpandedAppWidget>,
        customWidgets: List<Item>,
        settings: ExpandedSettings,
        viewState: ExpandedViewState
    ): List<Item> {
        val isDark = settings.isDark
        val list = ArrayList<Item>()
        if(settings.searchItem != null) {
            list.add(settings.searchItem.copy(topInset = viewState.topInset))
        }else{
            list.add(Item.StatusBarSpace(viewState.topInset, isDark))
        }
        val splitLists = split {
            it.page.smartspaceTargetId.startsWith(BLANK_TARGET_PREFIX)
        }
        splitLists.first.forEach {
            list.add(Item.Target(it.page, it.target?.id, isDark))
            list.addAll(
                it.getExtras(
                    surface,
                    widgets,
                    settings.enhancedEnabled,
                    isDark,
                    settings.useGoogleSans
                )
            )
        }
        val complications = splitLists.second.map { it.page }
        if(complications.isNotEmpty()) {
            list.add(Item.Complications(complications.extractComplications(), isDark))
        }
        list.addAll(customWidgets)
        return list
    }

    private fun List<SmartspaceTarget>.extractComplications(): ExpandedComplications {
        return map { target ->
            val header = target.templateData?.subtitleItem?.let {
                Complication.SubItemInfo(target, it)
            } ?: target.headerAction?.let {
                Complication.Action(target, it)
            }
            val base = target.templateData?.subtitleSupplementalItem?.let {
                Complication.SubItemInfo(target, it)
            } ?: target.baseAction?.let {
                Complication.Action(target, it)
            }
            listOfNotNull(header, base)
        }.flatten().filter {
            it.isValid()
        }.let {
            ExpandedComplications(it)
        }
    }

    private suspend fun SmartspacePageHolder?.getExtras(
        surface: UiSurface,
        widgets: List<ExpandedAppWidget>,
        enhanced: Boolean,
        isDark: Boolean,
        useGoogleSans: Boolean
    ): List<Item> {
        if(this == null || target == null) return emptyList()
        val isLocked = surface == UiSurface.LOCKSCREEN
        val items = ArrayList<Item>()
        val config = target.config
        val expanded = page.expandedState
        val widget = expanded?.widget
        val appShortcuts = expanded?.appShortcuts
        val shouldLoadAppShortcuts = config.showAppShortcuts && enhanced && appShortcuts != null
                && (appShortcuts.showWhenLocked || !isLocked)
        if(config.showWidget && widget != null && (widget.showWhenLocked || !isLocked)){
            val id = widgets.firstOrNull {
                it.componentName == widget.info.provider.flattenToString() && it.id == widget.id
            }?.appWidgetId
            @Suppress("CloseWidget")
            items.add(Item.Widget(
                widget.info,
                id,
                widget.id,
                widget.width,
                widget.height,
                isCustom = false,
                config = null,
                useGoogleSans = useGoogleSans,
                isDark = isDark
            ))
        }
        val remoteViews = expanded?.remoteViews
        if(config.showRemoteViews && remoteViews != null) {
            val views = if(isLocked){
                remoteViews.locked
            }else{
                remoteViews.unlocked
            }
            if(views != null){
                items.add(Item.RemoteViews(views, page.smartspaceTargetId, isDark))
            }
        }
        val shortcuts = expanded?.shortcuts?.shortcuts
        val shortcutsToShow = ArrayList<BaseShortcut>()
        if(shouldLoadAppShortcuts && appShortcuts != null){
            shortcutsToShow.addAll(appShortcuts.getAppShortcuts())
        }
        if(config.showShortcuts && shortcuts != null){
            val filteredShortcuts = shortcuts.filter {
                it.showWhenLocked || !isLocked
            }.take(MAX_SHORTCUTS)
            if(filteredShortcuts.isNotEmpty()) {
                shortcutsToShow.addAll(filteredShortcuts)
            }
        }
        if(shortcutsToShow.isNotEmpty()){
            items.add(Item.Shortcuts(shortcutsToShow, page.smartspaceTargetId, isDark))
        }
        return items
    }

    private suspend fun loadAppShortcuts(): List<ShortcutInfo>? {
        if(appShortcuts != null) return appShortcuts
        val appShortcuts = try {
            shizukuServiceRepository.runWithService {
                it.getShortcuts(ShortcutQueryWrapper(
                    FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST
                ))
            }.unwrap() as? ParceledListSlice<ShortcutInfo>
        }catch (e: SecurityException){
            return emptyList()
        }
        this.appShortcuts = appShortcuts?.list
        return this.appShortcuts
    }

    /**
     *  Progressively loads app shortcuts from the full list, until either none are left which match
     *  the criteria (specified package names) or the limit ([MAX_SHORTCUTS]) is reached.
     */
    private suspend fun ExpandedState.AppShortcuts.getAppShortcuts(): List<AppShortcut> {
        val limit = appShortcutCount.coerceAtMost(MAX_SHORTCUTS)
        val appShortcuts = loadAppShortcuts()?.groupBy {
            it.`package`
        }?.filter {
            packageNames.contains(it.key)
        }?.mapValues {
            it.value.sortedBy { shortcut -> shortcut.rank }
        }?.map {
            LinkedList(it.value)
        } ?: return emptyList()
        val shortcuts = ArrayList<ShortcutInfo>()
        while(!appShortcuts.all { it.isEmpty() }) {
            appShortcuts.forEach {
                val shortcut = it.pop()
                if(shortcuts.size > limit) return@forEach
                shortcuts.add(shortcut)
            }
        }
        return shortcuts.mapNotNull {
            it.getAppShortcut()
        }
    }

    /**
     *  Loads a given [ShortcutInfo]'s icon into the [AppShortcut] model
     */
    private suspend fun ShortcutInfo.getAppShortcut(): AppShortcut? {
        val icon = shizukuServiceRepository.runWithService {
            it.getAppShortcutIcon(`package`, id)
        }.unwrap() ?: return null
        return AppShortcut(shortLabel ?: longLabel ?: id, icon, `package`, id)
    }

    override fun toSmartspacerSessionId(id: SmartspaceSessionId): SmartspaceSessionId {
        return id
    }

    sealed class Item(val type: Type, open val isDark: Boolean) {
        data class StatusBarSpace(
            val topInset: Int,
            override val isDark: Boolean
        ): Item(Type.STATUS_BAR_SPACE, isDark)

        data class Search(
            val doodleImage: DoodleImage?,
            val searchApp: SearchApp?,
            val searchBackgroundColor: Int?,
            val isLightStatusBar: Boolean,
            val topInset: Int = 0,
            override val isDark: Boolean
        ): Item(Type.SEARCH, isDark)

        data class Target(
            val target: SmartspaceTarget,
            val parentId: String?,
            override val isDark: Boolean
        ): Item(Type.TARGET, isDark) {
            override fun getStaticId() = "${parentId ?: "default"}_${target.smartspaceTargetId}"
        }

        data class Complications(
            val complications: ExpandedComplications,
            override val isDark: Boolean
        ): Item(Type.COMPLICATIONS, isDark)

        data class Widget(
            val provider: AppWidgetProviderInfo,
            val appWidgetId: Int?,
            val id: String?,
            val width: Int,
            val height: Int,
            val isCustom: Boolean,
            val useGoogleSans: Boolean,
            val config: CustomExpandedAppWidgetConfig?,
            override val isDark: Boolean
        ): Item(Type.WIDGET, isDark) {
            override fun getStaticId() = "widget_$appWidgetId"

            override fun equals(other: Any?): Boolean {
                if(other !is Widget) return false
                if(other.provider.provider != provider.provider) return false
                if(other.appWidgetId != appWidgetId) return false
                if(other.id != id) return false
                if(other.width != width) return false
                if(other.height != height) return false
                if(other.isCustom != isCustom) return false
                if(other.useGoogleSans != useGoogleSans) return false
                if(other.config != config) return false
                if(other.isDark != isDark) return false
                return true
            }

            override fun hashCode(): Int {
                var result = provider.hashCode()
                result = 31 * result + (appWidgetId ?: 0)
                result = 31 * result + (id?.hashCode() ?: 0)
                result = 31 * result + width
                result = 31 * result + height
                result = 31 * result + isCustom.hashCode()
                result = 31 * result + useGoogleSans.hashCode()
                result = 31 * result + (config?.hashCode() ?: 0)
                result = 31 * result + isDark.hashCode()
                return result
            }
        }

        data class RemovedWidget(
            val appWidgetId: Int?,
            override val isDark: Boolean
        ): Item(Type.REMOVED_WIDGET, isDark) {
            override fun getStaticId() = "removed_widget_$appWidgetId"
        }

        data class RemoteViews(
            val remoteViews: android.widget.RemoteViews,
            val parentId: String,
            override val isDark: Boolean
        ): Item(Type.REMOTE_VIEWS, isDark) {
            override fun getStaticId() = "remote_views_$parentId"
        }

        data class Shortcuts(
            val shortcuts: List<BaseShortcut>,
            val parentId: String,
            override val isDark: Boolean
        ): Item(Type.SHORTCUTS, isDark) {
            override fun getStaticId() = "shortcuts_$parentId"
        }

        data class Footer(
            val hasClickedAdd: Boolean,
            override val isDark: Boolean
        ): Item(Type.FOOTER, isDark)

        open fun getStaticId(): String = type.name

        enum class Type {
            STATUS_BAR_SPACE,
            SEARCH,
            TARGET,
            COMPLICATIONS,
            WIDGET,
            REMOVED_WIDGET,
            REMOTE_VIEWS,
            SHORTCUTS,
            FOOTER
        }
    }

    data class ExpandedSettings(
        val enhancedEnabled: Boolean,
        val searchItem: Item.Search?,
        val isDark: Boolean,
        val useGoogleSans: Boolean
    )

    data class ExpandedViewState(
        val topInset: Int,
        val hasClickedAdd: Boolean
    )

    init {
        onCreate()
    }

}