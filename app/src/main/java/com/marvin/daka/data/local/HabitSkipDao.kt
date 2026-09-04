package com.marvin.daka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.marvin.daka.model.HabitSkip
import kotlinx.coroutines.flow.Flow

/**
 * 「跳过当天」记录表（habit_skips）的操作接口。
 *
 * 写操作都是 suspend；exists / getDates 给闹钟接收器和 Streak 计算用，
 * observeAll 给 ViewModel 汇总成「习惯 → 跳过日期集合」的 Map，驱动界面和连击。
 */
@Dao
interface HabitSkipDao {

    /** 插入一条跳过记录。唯一索引保证重复插不报错（IGNORE） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(skip: HabitSkip)

    /** 恢复备份用：已存在（唯一索引冲突）就更新，不存在就插入 */
    @Upsert
    suspend fun upsertAll(skips: List<HabitSkip>)

    /** 某习惯在某天是否被跳过 */
    @Query("SELECT COUNT(*) FROM habit_skips WHERE habitId = :habitId AND skipDate = :date")
    suspend fun exists(habitId: Long, date: String): Int

    /** 某习惯的全部跳过日期（给 Streak 排除用） */
    @Query("SELECT skipDate FROM habit_skips WHERE habitId = :habitId")
    suspend fun getDates(habitId: Long): List<String>

    /** 全部跳过记录（备份导出用） */
    @Query("SELECT * FROM habit_skips")
    suspend fun getAll(): List<HabitSkip>

    /** 响应式：全部跳过记录，给 ViewModel 汇总成 Map */
    @Query("SELECT * FROM habit_skips")
    fun observeAll(): Flow<List<HabitSkip>>

    /** 取消某习惯某天的跳过（再次点「跳过」想反悔时用） */
    @Query("DELETE FROM habit_skips WHERE habitId = :habitId AND skipDate = :date")
    suspend fun delete(habitId: Long, date: String)

    /** 习惯硬删除时清掉它的全部跳过（软归档一般走不到；留着防意外） */
    @Query("DELETE FROM habit_skips WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}
