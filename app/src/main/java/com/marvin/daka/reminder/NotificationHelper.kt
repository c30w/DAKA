package com.marvin.daka.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marvin.daka.MainActivity
import com.marvin.daka.R
import com.marvin.daka.model.Habit

/**
 * 发通知的封装。
 *
 * 三件事一件都不能少，缺一个通知就发不出来（而且不报错，静默失败，特别难查）：
 *
 * 1. **通知渠道**（Android 8 / API 26 起强制）。
 *    没有渠道，通知直接被丢弃。渠道只需创建一次，重复创建是幂等的，所以每次发之前调一下无妨。
 *
 * 2. **POST_NOTIFICATIONS 权限**（Android 13 / API 33 起）。
 *    这是危险权限，必须动态申请。没授权时 notify() 会静默失败——
 *    所以这里主动检查，没权限就干脆不发（而不是发了才被打回）。
 *
 * 3. **PendingIntent**：点通知要能跳回 App。
 *    onReceive 里拿不到 Activity，必须靠它把「点击」这个动作交给系统。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "daka_reminder"
    private const val CHANNEL_NAME = "打卡提醒"

    /**
     * 每条习惯提醒用**不同的通知 id**。
     *
     * 如果所有习惯共用一个 id，第二条通知会把第一条**替换**掉——
     * 用户早上连着收到三个习惯的提醒，最后通知栏里只剩最后一条。
     * 用 habitId 错开，三条并排显示，各自可以单独划掉。
     */
    private const val NOTIFICATION_ID_BASE = 2000

    /** 建通知渠道。8.0 以下系统没有渠道概念，直接跳过。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT // 会响铃、但不弹横幅打扰
        ).apply {
            description = "每天提醒还没完成的习惯"
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Android 13 起通知需要动态授权，这里检查有没有拿到 */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 点通知要跳回的意图。多个习惯共用 requestCode 0 没问题——它们是同一个目标页面 */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            // FLAG_IMMUTABLE 是 Android 12 起的硬性要求
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 发一条单个习惯的提醒（V3 主力用法）。
     *
     * @param habit 到点该做的那个习惯
     */
    fun showHabitReminder(context: Context, habit: Habit) {
        if (!hasPermission(context)) return

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("${habit.emoji} 该做「${habit.name}」了")
            .setContentText("点一下打开 DAKA 完成今天的打卡")
            .setContentIntent(contentIntent(context))
            // 点掉就没，不留在通知栏里积灰
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_BASE + habit.id.toInt(), notification)
    }
}
