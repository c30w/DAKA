package com.marvin.daka.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.marvin.daka.data.local.DatabaseProvider
import com.marvin.daka.model.Habit
import com.marvin.daka.model.ReminderLike

/**
 * 提醒的闹钟调度（V3：每个习惯一个闹钟 → V6：每个习惯的每条提醒各一个闹钟）。
 *
 * V2 的时候全局只有一个闹钟、一个时间，到点通知「还有 N 个习惯没打卡」。
 * V3 要求每个习惯单独设时间、单独设重复规则，一个闹钟就不够了——
 * 现在每个开启提醒的习惯各排一个独立闹钟，各自按自己的规则响。
 * V6 进一步：一个习惯可以有多条提醒（主提醒 + 附加提醒），每条提醒各排一个闹钟。
 *
 * 继续用 AlarmManager（而不是 WorkManager）的理由没变：
 * WorkManager 是「可延迟的后台任务」，系统为了省电可以推迟几十分钟甚至几小时，
 * 「明早 8 点提醒我」被推迟到 11 点，功能就废了。闹钟要的是**到点就响**。
 *
 * 四个必须知道的坑（V6 在 V3 基础上多了「一条提醒一个 requestCode」）：
 *
 * 1. **精确闹钟要授权**（Android 12 / API 31 起，Android 14 起默认不授予）。
 *    没授权时直接调 setExact 会抛 SecurityException 直接崩。
 *    每个调用点都要先过 canScheduleExactAlarms() 这一关。
 *
 * 2. **PendingIntent 靠 requestCode 区分**。
 *    如果所有提醒共用同一个 requestCode，后一个排期会**覆盖**前一个。
 *    V6 用 `requestCode(habitId, reminderId)` 把每条提醒编成唯一码，
 *    主提醒 reminderId=0，附加提醒 reminderId=它的主键 id。
 *
 * 3. **意图还要带 data**。
 *    部分国产 ROM 在匹配 PendingIntent 时会忽略 extras，只看 action + data。
 *    给每条提醒一个不同的 data URI（`daka://remind/$habitId/$reminderId`），
 *    保证它们真的被当成不同的意图。
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

    /** habitId 取低 16 位左移 12 位，reminderId 取低 12 位，拼成全局唯一的 requestCode。
     *  个人 App 习惯数/提醒数远小于 2^16 / 2^12，碰撞概率可忽略。 */
    private const val RC_HABIT_BITS = 12

    private fun requestCode(habitId: Long, reminderId: Long): Int =
        REQUEST_CODE_BASE + ((habitId.toInt() and 0xFFFF) shl RC_HABIT_BITS) + (reminderId.toInt() and 0xFFF)

    /** 某条提醒的 PendingIntent。requestCode 和 data 都带 habitId + reminderId，双保险。 */
    private fun pendingIntent(context: Context, habitId: Long, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("daka://remind/$habitId/$reminderId")
            putExtra(ReminderReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(habitId, reminderId),
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
     * 给「一条提醒」排下一次触发。主提醒和附加提醒都走这里。
     *
     * 规则计算全部交给 [ReminderRule]，这里只管「拿到时间戳 → 交给系统」。
     *
     * @return 是否排成功。false 有三种可能：提醒没开 / 规则已经用完 / 往后可预见的未来都匹配不上 /
     *         没拿到精确闹钟授权。前三种是正常情况，只有最后一种需要界面去引导用户授权。
     */
    fun scheduleReminder(context: Context, reminder: ReminderLike): Boolean {
        // 关了提醒、或者「提醒 N 次」已经用完 —— 顺手把可能存在的旧闹钟取消掉
        if (!reminder.reminderEnabled || ReminderRule.isExhausted(reminder)) {
            cancelReminder(context, reminder.habitId, reminder.reminderId)
            return false
        }

        val triggerAt = ReminderRule.nextTriggerMillis(reminder) ?: run {
            // 往后 400 天都匹配不上（例如「每月 31 号」）：没什么可排的，清掉旧的
            cancelReminder(context, reminder.habitId, reminder.reminderId)
            return false
        }

        if (!canScheduleExact(context)) return false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context, reminder.habitId, reminder.reminderId)
        )
        return true
    }

    /**
     * 给一个习惯排全部提醒：主提醒（在 habit 对象上，reminderId=0）+ 所有附加提醒（库里查）。
     *
     * 一次性把「主 + 附加」全排了，调用方（ViewModel）不用关心提醒到底有几条、存在哪。
     */
    suspend fun scheduleHabit(context: Context, habit: Habit): Boolean {
        val all = mutableListOf<ReminderLike>(habit)
        runCatching { DatabaseProvider.get(context).reminderDao().getByHabit(habit.id) }
            .getOrNull()?.let { all += it }
        var any = false
        all.forEach { if (scheduleReminder(context, it)) any = true }
        return any
    }

    /** 取消一条提醒的闹钟。删除提醒、关闭提醒时都要调。 */
    fun cancelReminder(context: Context, habitId: Long, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, habitId, reminderId))
    }

    /** 取消一个习惯的全部闹钟（主 + 附加）。附加提醒的 id 要从库里查 */
    suspend fun cancelHabit(context: Context, habitId: Long) {
        cancelReminder(context, habitId, 0L)
        runCatching { DatabaseProvider.get(context).reminderDao().getByHabit(habitId) }
            .getOrNull()?.forEach { cancelReminder(context, habitId, it.id) }
    }

    /**
     * 重排**全部**开启提醒的习惯（含它们的附加提醒）。
     *
     * 调用时机：开机 / App 被系统杀掉后（闹钟全没了，必须重建）、用户批量改了设置。
     */
    suspend fun rescheduleAll(context: Context) {
        val habits = DatabaseProvider.get(context).habitDao().getReminderEnabled()
        habits.forEach { scheduleHabit(context, it) }
    }
}
