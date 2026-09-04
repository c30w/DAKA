package com.marvin.daka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.marvin.daka.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * 附加提醒表（reminders）的操作接口。
 *
 * 和 [HabitDao] 一样：查询返回 Flow 自动刷新；写操作是 suspend，必须在非主线程。
 * 主提醒（在 habits 表上）不在这里，这里只管「主提醒之外」的那些。
 */
@Dao
interface ReminderDao {

    /** 插入一条附加提醒，返回分配的 id */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: Reminder): Long

    /** 批量插入（恢复备份、模板导入时用） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reminders: List<Reminder>): List<Long>

    /** 导入用：已存在（主键相同）就覆盖，不存在就插入 */
    @Upsert
    suspend fun upsertAll(reminders: List<Reminder>)

    /** 整体更新一条（改了时间/规则后用） */
    @Update
    suspend fun update(reminder: Reminder)

    /** 按 id 取一条。闹钟接收器触发时要拿它的「最新设置」 */
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    /** 某个习惯的全部附加提醒（按 id 升序，保证显示顺序稳定） */
    @Query("SELECT * FROM reminders WHERE habitId = :habitId ORDER BY id ASC")
    suspend fun getByHabit(habitId: Long): List<Reminder>

    /** 响应式版本：习惯的附加提醒变化时自动推送（日历/编辑页用） */
    @Query("SELECT * FROM reminders WHERE habitId = :habitId ORDER BY id ASC")
    fun observeByHabit(habitId: Long): Flow<List<Reminder>>

    /** 全部附加提醒（响应式，给 ViewModel 汇总成 Map 用） */
    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<Reminder>>

    /** 导出用：读取全部附加提醒，包括任何孤儿（恢复时按外键合并） */
    @Query("SELECT * FROM reminders")
    suspend fun getAllForBackup(): List<Reminder>

    /** 提醒触发后把已触发次数 +1（SQL 自增，避免并发覆盖） */
    @Query("UPDATE reminders SET firedCount = firedCount + 1 WHERE id = :id")
    suspend fun incrementFiredCount(id: Long)

    /** 改了规则时把计数清零 */
    @Query("UPDATE reminders SET firedCount = 0 WHERE id = :id")
    suspend fun resetFiredCount(id: Long)

    /** 删除一条附加提醒 */
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 删除某习惯的全部附加提醒（整体编辑提醒时先清后插，做全量替换） */
    @Query("DELETE FROM reminders WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}
