package com.marvin.daka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.marvin.daka.model.Habit
import kotlinx.coroutines.flow.Flow

/**
 * 习惯表的操作接口。
 *
 * @Dao 是 Room 的核心：你只写接口和 SQL，**实现类由 Room 在编译期生成**（KSP 的活）。
 * 你写的代码里找不到 HabitDao_Impl，但编译产物里一定有。
 *
 * 两个必须记住的规则：
 *
 * 1. **返回 Flow 的查询是异步的**，可以在主线程直接 collect，Room 会自己切到后台线程去查，
 *    而且数据一变自动推送新值——这就是「改完数据库，界面自己刷新」的原理。
 *
 * 2. **写操作必须是 suspend，并且要在非主线程调用。**
 *    在主线程读写数据库，Room 会直接抛异常崩掉：
 *    `Cannot access database on the main thread`
 *    所以调用方要用 Dispatchers.IO（M4 之后交给 ViewModel 的 viewModelScope）。
 */
@Dao
interface HabitDao {

    /**
     * 观察所有未归档的习惯。
     *
     * 排序规则（V4.1）：sortOrder（用户拖出来的顺序）→ 创建时间兜底
     * （老数据 sortOrder 全是 0，自然退回原来的顺序，升级无感）。
     *
     * 注意：**首页的实际显示顺序由 ViewModel 的 sections 决定**（先按分类分组、
     * 分组内再按这里的顺序）。置顶不再参与 SQL 排序——置顶 = 跳到分类顶部
     * （写一个更小的 sortOrder），保证显示顺序和 sortOrder 永远一致，
     * 否则拖拽排序的换位计算会全部错位。
     *
     * 返回 Flow 而不是 List：数据库里的数据变了，这个 Flow 会自动 emit 新列表。
     * archivedAt IS NULL 用于过滤掉已归档的（软删除）。
     */
    @Query(
        """
        SELECT * FROM habits WHERE archivedAt IS NULL
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeAll(): Flow<List<Habit>>

    /**
     * 批量插入，返回被插入行的 id 列表。
     *
     * onConflict = IGNORE：遇到重复（这里主要靠主键）就跳过，不报错。
     * 返回 id 列表是为了「插入习惯后紧接着插入它的打卡记录」——
     * 记录需要 habitId，而 habitId 要等数据库分配了才知道。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(habits: List<Habit>): List<Long>

    /** 未归档习惯的数量。用于判断「是不是第一次启动，要不要灌示例数据」。 */
    @Query("SELECT COUNT(*) FROM habits WHERE archivedAt IS NULL")
    suspend fun count(): Int

    /**
     * 归档（软删除）一个习惯。
     *
     * 为什么是 UPDATE 而不是 DELETE？
     * habit_records 表对 habits 有外键级联删除，**真删会把这个习惯的全部历史打卡记录一起带走**。
     * 用户点删除往往只是「不想在首页看到它了」，历史数据他可能还想留着备份导出。
     * 所以打一个归档标记，列表查询用 `archivedAt IS NULL` 过滤掉即可。
     *
     * 代价：这些记录会一直留在库里。自用 App 无所谓，将来要真清理，
     * 得写一个「清空 90 天前已归档习惯及其记录」的维护操作。
     */
    @Query("UPDATE habits SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: Long = System.currentTimeMillis())

    /**
     * 导出用：读取**全部**习惯，包括已归档的。
     *
     * 注意这里故意不带 `WHERE archivedAt IS NULL`。
     * 备份的意义是「什么都不丢」——用户归档过的习惯，其历史打卡记录还在库里，
     * 如果导出时只带上未归档的习惯，那些记录就成了没有主人的孤儿，恢复时会因外键约束失败。
     */
    @Query("SELECT * FROM habits")
    suspend fun getAllForBackup(): List<Habit>

    /**
     * 查**未归档**的习惯（一次性读取，不是 Flow）。
     *
     * 给闹钟接收器用：它要在后台算「今天还有几个没打卡」，
     * 需要的是「现在的值」，不需要持续订阅，所以用 suspend 而不是 Flow。
     */
    @Query("SELECT * FROM habits WHERE archivedAt IS NULL")
    suspend fun getAllActive(): List<Habit>

    /**
     * 导入用：批量写入，已存在（主键相同）就覆盖，不存在就插入。
     *
     * @Upsert 是 Room 2.5 加的，等价于「INSERT ... ON CONFLICT DO UPDATE」。
     * 没有它的时候得写 @Insert(onConflict = REPLACE)，但 REPLACE 的语义是
     * **先删再插**，会触发外键级联（把关联的打卡记录一起删掉）——这是个隐蔽的大坑。
     * Upsert 是真的「更新」，不做删除，所以更安全。
     */
    @Upsert
    suspend fun upsertAll(habits: List<Habit>)

    // ---------------- V3：每习惯独立提醒 ----------------

    /** 按 id 取一个习惯。闹钟接收器和提醒编辑页用它拿当前设置。 */
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    /** 所有「开着提醒、且没归档」的习惯。开机重排、批量同步时用它。 */
    @Query("SELECT * FROM habits WHERE archivedAt IS NULL AND reminderEnabled = 1")
    suspend fun getReminderEnabled(): List<Habit>

    /**
     * 更新一个习惯的提醒设置。
     *
     * 用一条 UPDATE 改 11 个列，而不是 @Update 传整个对象，有两个原因：
     *   1. @Update 会把对象里所有字段都写回去，万一调用方手上的是「改之前查出来的旧对象」，
     *      就会把别人刚改过的字段覆盖掉（典型的并发覆盖 bug）
     *   2. 闹钟那边会单独改 firedCount，用窄口径 UPDATE 不会互相踩踏
     */
    @Query(
        """
        UPDATE habits SET
            reminderEnabled = :enabled,
            reminderHour = :hour,
            reminderMinute = :minute,
            repeatType = :repeatType,
            repeatInterval = :interval,
            repeatWeekdays = :weekdays,
            repeatMonthDays = :monthDays,
            endType = :endType,
            repeatTimes = :times,
            remindEndDate = :endDate,
            remindStartDate = :startDate,
            firedCount = :firedCount
        WHERE id = :id
        """
    )
    @Suppress("LongParameterList")
    suspend fun updateReminder(
        id: Long,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        repeatType: Int,
        interval: Int,
        weekdays: String,
        monthDays: String,
        endType: Int,
        times: Int,
        endDate: String,
        startDate: String,
        firedCount: Int
    )

    /**
     * 提醒触发后把已触发次数 +1。
     *
     * 用 SQL 自增而不是「查出来 +1 再写回」，是为了避免竞态：
     * 万一两个闹钟几乎同时到点，查-改-写会丢掉一次计数。
     */
    @Query("UPDATE habits SET firedCount = firedCount + 1 WHERE id = :id")
    suspend fun incrementFiredCount(id: Long)

    /** 改提醒设置时把计数清零（用户重新配了规则，旧的触发次数不该继承） */
    @Query("UPDATE habits SET firedCount = 0 WHERE id = :id")
    suspend fun resetFiredCount(id: Long)

    // ---------------- V4：分类 / 置顶 / 排序 / 整体编辑 ----------------

    /** 更新整行。编辑习惯（名称/图标/颜色/分类等）用窄口径 @Update 的原因见 updateReminder */
    @Update
    suspend fun update(habit: Habit)

    /** 置顶 / 取消置顶 */
    @Query("UPDATE habits SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** 改单个习惯的排序号。移动位置 = 相邻两个习惯互换这个值 */
    @Query("UPDATE habits SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** V4.2：改分类。跨分类拖动时用，窄口径 UPDATE 不碰其他字段 */
    @Query("UPDATE habits SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)

    /** 当前最大的排序号。新建习惯取它 +1，站到队尾 */
    @Query("SELECT MAX(sortOrder) FROM habits WHERE archivedAt IS NULL")
    suspend fun getMaxSortOrder(): Int?
}
