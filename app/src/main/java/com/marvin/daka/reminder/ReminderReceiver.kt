package com.marvin.daka.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marvin.daka.data.local.DatabaseProvider
import com.marvin.daka.model.ReminderLike
import com.marvin.daka.util.todayString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟到点的接收器（V3：按 habitId 处理单个习惯 → V6：按 habitId + reminderId 处理单条提醒）。
 *
 * 收到广播后干几件事：
 *   1. 从 intent 里拿 habitId + reminderId，去库里查这条提醒的最新设置
 *   2. 今天已经打过卡 → 静默跳过（不打扰，这是 V2 就定下的原则）
 *   3. 今天被「跳过」了 → 也不打扰、也不计触发次数（见 [HabitSkip]），但依然排下一次
 *   4. 没打卡也没跳过 → 发一条「该做 XX 了」的通知
 *   5. 没跳过就 firedCount +1，然后按规则排下一次（自续期）
 *
 * ⚠️ **goAsync() 是关键**。
 * BroadcastReceiver 的 onReceive 有一条铁律：必须在约 10 秒内返回，否则系统判定 ANR。
 * 但我们要查数据库，是异步的——等协程跑完早已超过 10 秒。goAsync() 告诉系统
 * 「我还没完事，先别杀我」，干完活必须调 finish()，否则系统会一直挂着这个广播不放。
 *
 * ⚠️ **为什么每次都要重新查库，而不是把提醒对象塞进 intent？**
 * intent 里能放 Parcelable，技术上可行。但那是个陷阱：闹钟排的是「未来某一刻」，
 * 从排期到触发这段时间里，用户可能已经改了时间、关了提醒、甚至删了这个习惯。
 * 用旧对象发通知 = 发了条过期通知。所以只传 id，触发时读最新的——「单一数据源」在闹钟场景下的体现。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return

        // 拿不到 id 就什么都不做。绝不「兜底提醒全部习惯」——一条坏掉的广播不该变成一次骚扰
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (habitId <= 0L || reminderId < 0L) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DatabaseProvider.get(context)

                // 触发时读最新数据（理由见类注释）
                val habit = db.habitDao().getById(habitId) ?: return@launch

                // 归档（软删除）或关了主提醒的习惯：取消整组闹钟，别再响了
                if (habit.archivedAt != null || !habit.reminderEnabled) {
                    ReminderScheduler.cancelHabit(context, habitId)
                    return@launch
                }

                // 这条提醒本身：主提醒（reminderId=0）用 habit 上的字段；附加提醒查 reminders 表
                val reminder: ReminderLike? = if (reminderId == 0L) {
                    habit
                } else {
                    db.reminderDao().getById(reminderId)
                }

                if (reminder == null || !reminder.reminderEnabled) {
                    ReminderScheduler.cancelReminder(context, habitId, reminderId)
                    return@launch
                }

                val today = todayString()

                // 跳过当天：不提醒、不计触发次数，但仍排下一次（跳过 ≠ 结束）
                val skipped = db.habitSkipDao().exists(habitId, today) > 0
                val alreadyDone = db.habitRecordDao().exists(habitId, today)

                if (!skipped && !alreadyDone) {
                    NotificationHelper.showHabitReminder(context, habit)
                }

                // 「提醒 N 次后停止」的计数。跳过的那天不消耗次数。
                if (!skipped) {
                    if (reminderId == 0L) db.habitDao().incrementFiredCount(habitId)
                    else db.reminderDao().incrementFiredCount(reminderId)
                }

                // 排下一次。重新查一遍是为了拿到 +1 之后的 firedCount，
                // 这样「提醒 3 次后停止」在第 3 次触发后就不会再排第 4 次
                val next: ReminderLike? = if (reminderId == 0L) db.habitDao().getById(habitId)
                else db.reminderDao().getById(reminderId)
                next?.let { ReminderScheduler.scheduleReminder(context, it) }
            } finally {
                // 无论如何都要收尾，否则系统会一直挂着这个广播不放
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.marvin.daka.action.REMIND"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
