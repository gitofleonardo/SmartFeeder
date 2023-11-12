package com.kieronquinn.app.smartspacer.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.kieronquinn.app.smartspacer.R
import com.kieronquinn.app.smartspacer.repositories.SmartspacerSettingsRepository
import com.kieronquinn.app.smartspacer.utils.extensions.whenCreated
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import org.koin.android.ext.android.inject

class ExpandedProxyActivity: MonetCompatActivity() {

    companion object {
        fun createProxyIntent(context: Context): Intent {
            return Intent(context, ExpandedProxyActivity::class.java)
        }
    }

    private val settings by inject<SmartspacerSettingsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        whenCreated {
            if(launchMainIfRequired()) return@whenCreated
            monet.awaitMonetReady()
            setContentView(R.layout.activity_expanded_proxy)
        }
    }

    private suspend fun launchMainIfRequired(): Boolean {
        if(!settings.hasSeenSetup.get()){
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        }
        return false
    }

}