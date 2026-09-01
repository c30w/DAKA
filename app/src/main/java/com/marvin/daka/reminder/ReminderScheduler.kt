package com.marvin.daka.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.marvin.daka.data.local.DatabaseProvider
import com.marvin.daka.model.Habit

/**
 * 提醒的闹钟调度（V3：改成**每个习惯一个闹钟**）。
 *
 * V2 的时候全局只有一个闹钟、一个时间，到点通知「还有 N 个习惯没打卡」。
 * V3 要求每个习惯单独设时间、单独设重复规则，一个闹钟就不够了——
 * 现在每个开启提醒的习惯各排一个独立闹钟，各自按自己的规则响。
 *
 * 继续用 AlarmManager（而不是 WorkManager）的理由没变：
 * WorkManager 是「可延迟的后台任务」，系统为了省电可以推迟几十分钟甚至几小时，
 * 「明早 8 点提醒我」被推迟到 11 点，功能就废了。闹钟要的是**到点就响**。
 *
 * 四个必须知道的坑：
 *
 * 1. **精确闹钟要授权**（Android 12 / API 31 起，Android 14 起默认不授予）。
 *    没授权时直接调 setExact 会抛 SecurityException 直接崩。
 *    每个调用点都要先过 canScheduleExactAlarms() 这一关。
 *
 * 2. **PendingIntent 靠 requestCode 区分**。
 *    这是最容易被忽略的一点：如果所有习惯共用同一个 requestCode，
 *    后一个排期会**覆盖**前一个（FLAG_UPDATE_CURRENT 的语义就是更新同一个）。
 *    结果就是只有最后一个习惯能提醒。requestCode 必须带 habitId。
 *
 * 3. **意图还要带 data**。
 *    部分国产 ROM 在匹配 PendingIntent 时会忽略 extras，只看 action + data。
 *    给每个习惯一个不同的 data URI，能保证它们真的被当成不同的意图。
 *    （intent 的 extra 会被忽略是历史遗留行为，不防一手会踩到莫名其妙的坑。）
 *
 * 4. **这里排的是一次性闹钟，不是重复闹钟**。
 *    触发后由 ReminderReceiver 再排下一次。好处是每次都重读一遍最新设置
 *    （用户改了时间、删了习惯、打完了卡，全都能立刻体现）。
 *
 * 5. 进程被杀、手机重启后闹钟全部失效——Android 机制，无解，靠 BootReceiver 补。
 */
object ReminderScheduler {

    /** requestCode 基数。habitId 从 1 开始，加个基数避免和别处的请求码撞车。 */
    private const val REQUEST_CODE_BASE = 21_000

    /** 某个习惯的 PendingIntent。requestCode 和 data 都带 habitId，双保险。 */
    private fun pendingIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("daka://remind/$habitId")
            putExtra(ReminderReceiver.EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + habitId.toInt(),
            intent,
            // FLAG_IMMUTABLE：Android 12 起必须显式声明，否则直接崩。
            // FLAG_UPDATE_CURRENT：重复排期时更新已有 PendingIntent，别建新的
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Android 12 起要先问系统「我还能用精确闹钟吗」 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * 给一个习惯排下一次提醒。
     *
     * 规则计算全部交给 [ReminderRule]，这里只管「拿到时间戳 → 交给系统」。
     *
     * @return 是否排成功。false 有三种可能：习惯没开提醒 / 规则已经用完 / 没拿到精确闹钟授权。
     *         前两种是正常情况，只有第三种需要界面去引导用户授权。
     */
    fun scheduleHabit(context: Context, habit: Habit): Boolean {
        // 关了提醒、或者「提醒 N 次」已经用完 —— 顺手把可能存在的旧闹钟取消掉
        if (!habit.reminderEnabled || ReminderRule.isExhausted(habit)) {
            cancelHabit(context, habit.id)
            return false
        }

        val triggerAt = ReminderRule.nextTriggerMillis(habit) ?: run {
            // 往后 400 天都匹配不上（例如「每月 31 号」）：没什么可排的，清掉旧的
            cancelHabit(context, habit.id)
            return false
        }

        if (!canScheduleExact(context)) return false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context, habit.id)
        )
        return true
    }

    /** 取消某个习惯的闹钟。删除习惯、关闭提醒时都要调。 */
    fun cancelHabit(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, habitId))
    }

    /**
     * 重排**全部**开启提醒的习惯。
     *
     * 调用时机：
     *   - 开机 / App 被系统杀掉后（闹钟全没了，必须重建）
     *   - 用户批量改了设置
     *
     * 为什么不是「每次改一个习惯就全量重排」？
     * 因为 AlarmManager 的精确闹钟数量虽然不算稀缺资源，但频繁 cancel+set 毫无必要。
     * 单条改动走 [scheduleHabit] 即可，全量重排只用在批量场景。
     */
    suspend fun rescheduleAll(context: Context) {
        val habits = DatabaseProvider.get(context).habitDao().getReminderEnabled()
        habits.forEach { scheduleHabit(context, it) }
    }
}
