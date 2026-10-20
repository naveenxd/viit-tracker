package com.vignan.tracker.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.vignan.tracker.TrackerColors

/**
 * Play Store flavor's update hook.
 *
 * There is deliberately no APK download/install code here: Google Play owns
 * updates for this build. On first resume we ask Play whether an update is
 * available and let Play show its own flexible/immediate flow. If the app was
 * sideloaded (not installed by Play), the update info task simply fails and
 * this wrapper stays invisible.
 */
@Composable
fun UpdateOverlay(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val manager = remember(context) {
        runCatching { AppUpdateManagerFactory.create(context) }.getOrNull()
    }

    // A flexible update downloads in the background; don't restart the app
    // under the user. Show a small restart affordance instead.
    var updateReady by remember { mutableStateOf(false) }

    DisposableEffect(manager, activity) {
        if (manager == null || activity == null) {
            onDispose { }
        } else {
            val listener = InstallStateUpdatedListener { state ->
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    updateReady = true
                }
            }
            manager.registerListener(listener)

            // Check once per activity instance. If the activity is already
            // resumed when this effect runs, the observer would never see an
            // ON_RESUME event, so check immediately in that case.
            var checked = false
            val checkOnce = {
                if (!checked) {
                    checked = true
                    checkForPlayUpdate(manager, activity) { updateReady = true }
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) checkOnce()
            }
            activity.lifecycle.addObserver(observer)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                checkOnce()
            }

            onDispose {
                activity.lifecycle.removeObserver(observer)
                manager.unregisterListener(listener)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (updateReady) {
            Surface(
                onClick = {
                    updateReady = false
                    runCatching {
                        manager?.completeUpdate()?.addOnFailureListener {
                            updateReady = true
                        }
                    }.onFailure { updateReady = true }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    // Clear the floating tab pill, same offset as the GitHub
                    // updater's status toast.
                    .padding(bottom = 85.dp),
                shape = RoundedCornerShape(18.dp),
                color = TrackerColors.SurfaceElevated,
                border = BorderStroke(1.dp, TrackerColors.HairlineBorderLight),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Update downloaded",
                        fontSize = 12.5.sp,
                        color = TrackerColors.TextPrimary,
                    )
                    Text(
                        text = "Restart",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TrackerColors.PrimaryWhite,
                    )
                }
            }
        }
    }
}

private fun checkForPlayUpdate(
    manager: AppUpdateManager,
    activity: ComponentActivity,
    onUpdateReady: () -> Unit,
) {
    manager.appUpdateInfo
        .addOnSuccessListener { info ->
            when {
                info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    // A flexible update already downloaded while the app was
                    // backgrounded — let the user finish installing it.
                    onUpdateReady()
                }

                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                    // Play has no force_update flag; update priority is the
                    // equivalent. 4+ means "show the blocking immediate flow".
                    val type = when {
                        info.updatePriority() >= 4 &&
                            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                            AppUpdateType.IMMEDIATE

                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                            AppUpdateType.FLEXIBLE

                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                            AppUpdateType.IMMEDIATE

                        else -> null
                    }
                    if (type != null) {
                        runCatching {
                            manager.startUpdateFlow(
                                info,
                                activity,
                                AppUpdateOptions.newBuilder(type).build(),
                            )
                        }
                    }
                }
            }
        }
        .addOnFailureListener {
            // Not installed by Play, Play services unavailable, etc. — the
            // app keeps working and Play's normal auto-update still applies.
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
