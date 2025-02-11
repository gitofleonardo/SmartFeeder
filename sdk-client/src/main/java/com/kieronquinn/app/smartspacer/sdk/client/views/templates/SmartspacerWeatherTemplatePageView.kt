package com.kieronquinn.app.smartspacer.sdk.client.views.templates

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import com.kieronquinn.app.smartspacer.sdk.client.databinding.IncludeSmartspacePageTitleBinding
import com.kieronquinn.app.smartspacer.sdk.client.databinding.SmartspacePageTemplateWeatherBinding
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import java.util.Locale

class SmartspacerWeatherTemplatePageView(context: Context): SmartspacerBaseTemplatePageView<SmartspacePageTemplateWeatherBinding>(
    context,
    SmartspacePageTemplateWeatherBinding::inflate
) {

    private val dateFormat =
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE, MMM d")

    override val title: IncludeSmartspacePageTitleBinding? = null

    override val subtitle by lazy {
        SubtitleBinding.SubtitleAndAction(binding.smartspacePageTemplateBasicSubtitle)
    }

    override val supplemental by lazy {
        binding.smartspacePageTemplateBasicSupplemental
    }

    override suspend fun setTarget(
        target: SmartspaceTarget,
        interactionListener: SmartspaceTargetInteractionListener?,
        tintColour: Int
    ) {
        super.setTarget(target, interactionListener, tintColour)
        with(binding.smartspacePageTemplateBasicClock.smartspaceViewTitle){
            setTextColor(tintColour)
            format12Hour = dateFormat
            format24Hour = dateFormat
            setOnClickListener {
                context.launchCalendar()
            }
            setOnLongClickListener {
                interactionListener?.onLongPress(target)
                false
            }
        }
    }

    private fun Context.launchCalendar() {
        Intent(Intent.ACTION_VIEW).apply {
            data = ContentUris.appendId(
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
                System.currentTimeMillis()
            ).build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }.also {
            startActivity(it)
        }
    }

}