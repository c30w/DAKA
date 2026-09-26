package com.marvin.daka.quicktile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.marvin.daka.MainActivity
import com.marvin.daka.ShortcutActions

/**
 * 通知栏「快捷设置」磁贴（Quick Settings Tile）。
 *
 * 行为：
 *  - 单击（点一下）：直接打开「新建习惯」页（带 NEW_HABIT action，MainActivity 收到后导航过去）。
 *  - 长按：进入 App 主界面。
 *
 * ⚠️ 不同 Android 版本的交互模型不一样，这里分两套实现：
 *  - API < 34：单击走 [onClick]，长按由 qsTile.setActivity() 指定打开的 Activity。
 *    但 setActivity(Intent) 已从 SDK 37 的编译桩里移除，运行时在旧系统上仍在，故用反射调用。
 *  - API >= 34：单击直接由 setActivityLaunchForClick() 的 PendingIntent 拉起（不再回调 onClick）；
 *    长按改由 Manifest 里 MainActivity 的 ACTION_QS_TILE_PREFERENCES 接收，打开 App。
 *
 * 磁贴需要用户自己从「编辑快捷开关」面板里把 DAKA 加进通知栏。
 */
class NewHabitTileService : TileService() {

    /** 打开 App 主界面（长按用） */
    private fun appIntent() = Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 打开新建习惯页（单击用） */
    private fun newHabitIntent() = Intent(this, MainActivity::class.java).apply {
        action = ShortcutActions.NEW_HABIT
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            if (state != Tile.STATE_ACTIVE) state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API >= 34：单击（点一下）直接拉起新建习惯页
                val pi = android.app.PendingIntent.getActivity(
                    this@NewHabitTileService, 0, newHabitIntent(),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
                setActivityLaunchForClick(pi)
            } else {
                // API < 34：长按打开 App（setActivity 已从 37 编译桩移除，用反射）
                setLongPressActivity(appIntent())
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 仅在旧系统上：onClick 代表单击。参考长按（setActivity 系统级）的逻辑，
        // 先像长按那样「正常进入 App 主界面」，再由 App 启动后跳到新建习惯页。
        // API >= 34 单击已被 setActivityLaunchForClick 接管（系统级收起并新建），不会走到这里。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = appIntent().apply {
                putExtra(MainActivity.EXTRA_NEW_HABIT_AFTER_LAUNCH, true)
            }
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
            // MIUI 等定制 ROM 改写了 SystemUI，startActivityAndCollapse 往往只启动
            // Activity 而不收起快捷面板。补发 CLOSE_SYSTEM_DIALOGS 广播兜底收起。
            // 第三方 app 在 Android 12+ 发该广播受限，但多数 ROM（含 MIUI）仍放行，
            // 即使被系统拒绝也只是无效、不会崩溃，故 try-catch 包裹。
            collapsePanelsFallback()
        }
    }

    /** 兜底收起通知栏/快捷面板（应对 startActivityAndCollapse 在定制 ROM 不收起的情况） */
    private fun collapsePanelsFallback() {
        try {
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Throwable) {
            // 权限受限则忽略，不影响新建习惯页已正常打开
        }
    }

    /** 旧系统（<34）上通过反射调用 Tile.setActivity(Intent)，让长按打开指定 Activity。 */
    private fun setLongPressActivity(intent: Intent) {
        try {
            qsTile?.javaClass?.getMethod("setActivity", Intent::class.java)
                ?.invoke(qsTile, intent)
        } catch (_: Throwable) {
            // 反射失败则忽略，长按回退为系统默认行为
        }
    }
}
