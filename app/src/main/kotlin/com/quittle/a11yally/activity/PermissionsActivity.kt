package com.quittle.a11yally.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.quittle.a11yally.BuildConfig
import com.quittle.a11yally.PermissionsManager
import com.quittle.a11yally.R
import com.quittle.a11yally.activity.welcome.Welcome2Activity
import com.quittle.a11yally.analytics.firebaseAnalytics
import com.quittle.a11yally.analytics.logClick
import com.quittle.a11yally.base.ifElse
import com.quittle.a11yally.base.ifNotNull
import com.quittle.a11yally.base.resolveAttributeResourceValue
import com.quittle.a11yally.preferences.withPreferenceProvider

/**
 * Shows the current status of permissions necessary for the app to run effectively
 */
class PermissionsActivity : FixedContentActivity() {
    override val layoutId = R.layout.permissions_activity

    private val mPermissionsManager by lazy { PermissionsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        withPreferenceProvider(this) {
            if (getShowTutorial()) {
                startActivity(Intent(this@PermissionsActivity, Welcome2Activity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateViewsStatuses()

        // When granting overlay permissions, it can take some time between leaving the settings
        // activity (which is when it is updated) and the app getting the permissions. If the user
        // switches back via recents, the activity will likely get the update in time. If they use
        // the back button, however, they are very unlikely to get the permission by the time
        // onResume has been called so to handle this race condition, periodically update the views
        @Suppress("MagicNumber")
        Handler(mainLooper).run {
            val initialDelayMs = 500L
            postDelayed(this@PermissionsActivity::updateViewsStatuses, initialDelayMs)
            postDelayed(this@PermissionsActivity::updateViewsStatuses, initialDelayMs * 2)
            postDelayed(this@PermissionsActivity::updateViewsStatuses, initialDelayMs * 4)
        }
    }

    private fun updateViewsStatuses() {
        updateStatus(
            mPermissionsManager.hasDrawOverlaysPermission(),
            R.id.permission_overlay_wrapper,
            R.id.permission_overlay_image,
            R.id.permission_overlay_status,
            this::onClickFixOverlay
        )
        updateStatus(
            mPermissionsManager.hasAccessibilityServicePermission(),
            R.id.permission_service_wrapper,
            R.id.permission_service_image,
            R.id.permission_service_status,
            this::onClickFixService
        )

        findViewById<View>(R.id.continue_button).run {
            isEnabled = mPermissionsManager.hasAllPermissions()
            setOnClickListener {
                firebaseAnalytics.logClick(it)
                startActivity(Intent(this@PermissionsActivity, MainActivity::class.java))
            }
        }
    }

    private fun onClickFixOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !mPermissionsManager.hasDrawOverlaysPermission()
        ) {
            startActivityIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        } else {
            updateViewsStatuses()
        }
    }

    private fun onClickFixService() {
        if (!mPermissionsManager.hasAccessibilityServicePermission()) {
            try {
                startActivityIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS, true)
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivityIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS, false)
                } catch (e: ActivityNotFoundException) {
                    Log.w(BuildConfig.TAG, "Unable to show accessibility settings")
                }
            }
        } else {
            updateViewsStatuses()
        }
    }

    private fun updateStatus(
        hasPermission: Boolean,
        wrapperViewId: Int,
        imageViewId: Int,
        statusViewId: Int,
        onClickCallback: () -> Unit
    ) {
        val onClickListener: View.OnClickListener? = hasPermission.ifElse(
            null,
            object : View.OnClickListener {
                override fun onClick(view: View) {
                    firebaseAnalytics.logClick(view)
                    onClickCallback()
                }
            }
        )

        findViewById<View>(wrapperViewId).run {
            setOnClickListener(onClickListener)
            isEnabled = !hasPermission
            resolveAttributeResourceValue(
                hasPermission.ifElse(
                    R.attr.primary_action_disabled_background,
                    R.attr.primary_action_background
                )
            ).ifNotNull {
                setBackgroundColor(it)
            }
        }

        findViewById<ImageView>(imageViewId).run {
            setImageResource(
                hasPermission.ifElse(
                    R.drawable.service_status_enabled_icon,
                    R.drawable.warning_icon
                )
            )
        }

        findViewById<TextView>(statusViewId).run {
            setText(
                hasPermission.ifElse(
                    R.string.permissions_activity_status_ok,
                    R.string.permissions_activity_status_fix
                )
            )
        }
    }

    /**
     * Sends a start activity intent.
     * @param action The action of the intent
     * @param targetPackage If true, specifies the A11y Ally package as the uri of the intent
     */
    private fun startActivityIntent(action: String, targetPackage: Boolean) {
        val uri = targetPackage.ifElse(Uri.parse("package:$packageName"), null)
        val intent = Intent(action, uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}
