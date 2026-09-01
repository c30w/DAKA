package com.marvin.daka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 手机重启后重新排提醒。
 *
 * Android 在关机/重启时会**清空所有 AlarmManager 闹钟**——
 * 这不是 bug，是系统机制：闹钟只存在于内存里，进程没了就没了。
 * 所以必须监听开机广播，起来之后把闹钟重新排上，否则用户重启一次提醒就再也不响了。
 *
 * 注意国产 ROM（MIUI / ColorOS / HarmonyOS）普遍会**拦截开机广播**以省电，
 * 需要用户在「自启动管理」里手动放行 App。这个问题在应用层无解，只能引导。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // V3：每个开启提醒的习惯各排一个闹钟，这里全部重建。
                // 关着提醒的习惯不会被排（getReminderEnabled 只返回开启的）
                ReminderScheduler.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
