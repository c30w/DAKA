package com.marvin.daka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.marvin.daka.model.HabitRecord
import kotlinx.coroutines.flow.Flow

/**
 * 打卡记录表的操作接口。
 *
 * M3 的用法很朴素：整张表观察成一个 Flow，然后在 Kotlin 里算 streak 和 doneToday。
 * 个人用的 App 数据量很小（一天几条，一年一两千条），全表读进内存完全没压力。
 * 等真到了几万条，再改成「按 habitId 分组查询」或写带 JOIN 的 SQL，现在 premature。
 */
@Dao
interface HabitRecordDao {

    /** 观察全部打卡记录，按日期倒序（最新的在前）。 */
    @Query("SELECT * FROM habit_records ORDER BY date DESC")
    fun observeAll(): Flow<List<HabitRecord>>

    /**
     * 一次性读取某天的全部打卡记录（suspend，不是 Flow）。
     *
     * 给小组件用：组件在每次重绘前都要「现在的值」，而且是在写完库之后**紧接着**读，
     * 如果用 [observeAll] 的 Flow 再 `.first()`，Room 的失效通知还没传播，
     * 容易拿到写入前的旧值——表现就是「桌面点了没反应，等会儿才变」。
     * suspend 查询每次都真打库、拿到的是已提交的最新行，不受 Flow 缓存影响。
     */
    @Query("SELECT * FROM habit_records WHERE date = :date")
    suspend fun getByDate(date: String): List<HabitRecord>

    /**
     * 某个习惯在某天是否已打卡。
     *
     * 比 [getByDate] 后 `.any {}` 更省：一个 EXISTS 子查询让 SQLite 在找到第一行后就停，
     * 不用把当天记录整张搬进内存。闹钟接收器和小组件切换状态都只问这一句，值得单独写一条。
     */
    @Query("SELECT EXISTS(SELECT 1 FROM habit_records WHERE habitId = :habitId AND date = :date)")
    suspend fun exists(habitId: Long, date: String): Boolean

    /**
     * 插入一条打卡记录。
     *
     * onConflict = IGNORE 配合表上的 (habitId, date) 唯一索引：
     * 同一天重复打卡会被静默忽略，不会产生两行。
     * 这是「防止重复打卡」的最后一道防线，比在代码里先查再插可靠得多
     * （先查后插在多线程下仍有竞态，数据库约束才是真保证）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: HabitRecord)

    /** 批量插入，用于首次启动灌示例数据。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<HabitRecord>)

    /**
     * 取消打卡：删掉「某个习惯在某天」的那条记录。
     *
     * 用 @Query 手写 DELETE 而不是 @Delete，是因为 @Delete 要求传入完整对象，
     * 而我们手上只有 habitId 和 date，没有主键 id。
     */
    @Query("DELETE FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun deleteByDate(habitId: Long, date: String)

    /**
     * 导出用：读取全部打卡记录，按日期升序写入备份文件，方便人肉翻看。
     */
    @Query("SELECT * FROM habit_records ORDER BY date ASC")
    suspend fun getAllForBackup(): List<HabitRecord>

    /**
     * 导入用：存在就覆盖、不存在就插入。
     *
     * 用 @Upsert 而不是 @Insert(REPLACE)：REPLACE 会先删行再插，
     * 若有其他表外键指向它就会连带被删；Upsert 是纯更新，没这个副作用。
     *
     * ⚠️ 调用方必须**先导入 habits 再导入 records**：
     * 记录有指向 habits 的外键，习惯还没写进去就插记录，会直接违反外键约束崩溃。
     */
    @Upsert
    suspend fun upsertAll(records: List<HabitRecord>)
}
