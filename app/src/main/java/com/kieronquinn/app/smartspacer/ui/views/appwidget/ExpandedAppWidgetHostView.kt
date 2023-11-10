package com.kieronquinn.app.smartspacer.ui.views.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViewsHidden
import androidx.core.os.bundleOf
import com.kieronquinn.app.smartspacer.ui.views.RoundedCornersEnforcingAppWidgetHostView

class ExpandedAppWidgetHostView: RoundedCornersEnforcingAppWidgetHostView {

    constructor(context: Context): super(context)
    constructor(
        context: Context, interactionHandler: RemoteViewsHidden.InteractionHandler
    ): super(context, interactionHandler)
    constructor(
        context: Context, onClickHandler: RemoteViewsHidden.OnClickHandler
    ): super(context, onClickHandler)

    private val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
    private var widgetWidth = 0
    private var widgetHeight = 0

    var id: String? = null

    fun updateSizeIfNeeded(width: Int, height: Int) {
        val appWidgetId = appWidgetId
        widgetWidth = width
        widgetHeight = height
        val current = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val currentWidth = current.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val currentHeight = current.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if(currentWidth != width || currentHeight != height){
            val options = bundleOf(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH to width,
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT to height
            )
            try {
                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
            }catch (e: NullPointerException){
                //Xiaomi broke something
            }
        }
    }

    fun removeFromParentIfNeeded() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        if(this.appWidgetId == appWidgetId && this.appWidgetInfo.provider == info?.provider) {
            //Trying to set the same widget, skip
            return
        }
        super.setAppWidget(appWidgetId, info)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        val parentWidth = MeasureSpec.getSize(parentWidthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(parentHeightMeasureSpec)

        val lp = child.layoutParams as MarginLayoutParams
        val childWidth = parentWidth - widthUsed - paddingLeft - paddingRight - lp.leftMargin -
                lp.rightMargin
        val childHeight = parentHeight - heightUsed - paddingTop - paddingBottom - lp.topMargin-
                lp.bottomMargin

        val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        child.measure(childWidthSpec, childHeightSpec)
    }
}