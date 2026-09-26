package com.marvin.daka.ui.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.marvin.daka.R
import com.marvin.daka.reminder.NotificationHelper

/**
 * 提醒权限引导门：把「开提醒要过两道系统权限关」抽成可复用组件，
 * 新建习惯页和设置页共用同一套，避免两处各写一份、改漏一边。
 *
 * 两道关（Android 上的硬现实）：
 *   1. **POST_NOTIFICATIONS**（Android 13 / API 33 起）：运行时危险权限，弹系统框让用户点允许。
 *   2. **SCHEDULE_EXACT_ALARM**（Android 12 引入，Android 14 起默认不授予）：
 *      不弹框，只能跳系统设置页让用户手动开，且没有可靠回调——用户开了没有只能回来再查一次。
 *
 * 用法：
 * ```kotlin
 * val gate = rememberReminderPermissionGate(context) { msg -> snackbar/Toast }
 * gate.request(hasEnabledReminder = reminders.any { it.enabled }) {
 *     // 权限齐了（或压根没开提醒）→ 这里真正写库 / 排闹钟
 * }
 * ```
 * 被拒时 gate 自己用 [onMessage] 提示，**不会**调用 onReady；
 * 若还想在「被拒」时也做点什么（比如仍然创建习惯、不丢用户输入），传 [onDenied]。
 */
class ReminderPermissionGate internal constructor(
    private val request: (hasEnabledReminder: Boolean, onReady: () -> Unit, onDenied: (() -> Unit)?) -> Unit
) {
    /**
     * 保存前先确保权限。
     *
     * @param hasEnabledReminder 这次保存是否带着「开启的提醒」——没开就不需要权限，直接跑 onReady
     * @param onReady           权限齐了（或没开提醒）时调用，真正落库的地方
     * @param onDenied          申请过但被拒时调用（可选）。注意：被拒时 onReady 不会被调用
     */
    fun request(
        hasEnabledReminder: Boolean,
        onReady: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) {
        request(hasEnabledReminder, onReady, onDenied)
    }
}

/**
 * 创建并记住一个 [ReminderPermissionGate]。
 *
 * 把待执行的「真正保存」意图挂在两个 [androidx.compose.runtime.MutableState] 上，
 * launcher 的回调只读写这两个 state（不依赖 gate 实例本身），
 * 这样即便 gate 因重组被重建，权限流程也不会丢状态。
 *
 * @param context  上下文（默认取 LocalContext）
 * @param onMessage 被拒/缺权限时用来提示用户的回调（SettingsScreen 传 snackbar，导航层传 Toast）
 */
@Composable
fun rememberReminderPermissionGate(
    context: Context = LocalContext.current,
    onMessage: (String) -> Unit
): ReminderPermissionGate {
    // 待执行的「真正保存」意图；以及被拒时要不要兜底执行（如仍创建习惯）
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deniedFallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ⚠️ 两个 launcher 的声明顺序不能反：notificationLauncher 的回调里要调 exactAlarmLauncher
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val action = pendingAction
        pendingAction = null
        val fb = deniedFallback
        deniedFallback = null
        if (action == null) return@rememberLauncherForActivityResult
        if (hasExactAlarmPermission(context)) {
            action()
        } else {
            onMessage(context.getString(R.string.snack_alarm_perm))
            fb?.invoke()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAction
        if (action == null) return@rememberLauncherForActivityResult
        if (!granted) {
            pendingAction = null
            val fb = deniedFallback
            deniedFallback = null
            onMessage(context.getString(R.string.snack_notif_perm))
            fb?.invoke()
            return@rememberLauncherForActivityResult
        }
        // 通知过了，还有精确闹钟这一关。缺就跳设置页，pendingAction 先留着
        if (!hasExactAlarmPermission(context)) {
            exactAlarmLauncher.launch(exactAlarmSettingsIntent(context))
            return@rememberLauncherForActivityResult
        }
        pendingAction = null
        deniedFallback = null
        action()
    }

    return remember(context, onMessage) {
        ReminderPermissionGate { hasEnabledReminder, onReady, onDenied ->
            if (!hasEnabledReminder) {
                onReady()
                return@ReminderPermissionGate
            }
            // 第一关：通知权限
            if (!NotificationHelper.hasPermission(context)) {
                pendingAction = { onReady() }
                deniedFallback = onDenied
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@ReminderPermissionGate
            }
            // 第二关：精确闹钟权限
            if (!hasExactAlarmPermission(context)) {
                pendingAction = { onReady() }
                deniedFallback = onDenied
                exactAlarmLauncher.launch(exactAlarmSettingsIntent(context))
                return@ReminderPermissionGate
            }
            onReady()
        }
    }
}

/** Android 12 起要判断能不能用精确闹钟 */
fun hasExactAlarmPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

/** 跳系统「闹钟和提醒」授权页 */
fun exactAlarmSettingsIntent(context: Context) =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }
