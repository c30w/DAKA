package com.marvin.daka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitRecord

/**
 * 数据库本体。
 *
 * @Database 三要素：
 *   - entities：这个库里有哪几张表。**加新表必须登记在这里**，否则 Room 不认识，会编译报错。
 *   - version：结构版本号。改了表结构（加列、改类型）就要 +1，否则真机会崩。
 *   - exportSchema：是否把表结构导出成 JSON 存档。false = 不导出。
 *     正式项目建议 true（方便写迁移和回溯），学习阶段关掉少一个目录要管。
 *
 * 类是 abstract 的，只声明获取 DAO 的抽象方法 —— 实现由 Room 生成。
 */
@Database(
    entities = [Habit::class, HabitRecord::class],
    // version 4：V4 给 habits 表加了 category / sortOrder / pinned 三列，
    // 见下面的 MIGRATION_3_4。
    //
    // 演进记录：
    //   v1 → v2：M5 加 colorArgb 列
    //   v2 → v3：V3 加提醒字段组
    //   v3 → v4：V4 加分类/排序/置顶
    //
    // ⚠️ 改表结构必须 +1，否则真机上升级会直接崩（Room 会校验 schema 哈希）。
    version = 4,
    exportSchema = false
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
}

/**
 * v2 → v3 迁移：给 habits 表补上提醒相关的列。
 *
 * 为什么必须写这个 Migration，而不是继续靠 fallbackToDestructiveMigration？
 * 因为**真机上已经有数据了**——老陈的打卡记录、连续天数全在库里。
 * 破坏性迁移会把整库清空重建，用户打开 App 发现几十天的记录全没了，
 * 这种事故一次就够劝退了。
 *
 * 三条 SQLite 铁律：
 * 1. **ADD COLUMN 必须带 NOT NULL DEFAULT**。
 *    SQLite 不允许「加一个非空且没有默认值的列」——已有行填什么？它不知道。
 * 2. **只能逐列 ADD，不能一次加多个**（ADD COLUMN 一次只接受一列）。
 * 3. 默认值要和 Kotlin 数据类里的默认值**保持一致**，
 *    否则 Room 校验 schema 时会报「迁移后的表结构和 Entity 对不上」。
 *
 * 布尔值在 SQLite 里存 0/1（SQLite 没有布尔类型）。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN reminderHour INTEGER NOT NULL DEFAULT 21")
        db.execSQL("ALTER TABLE habits ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN repeatType INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN repeatInterval INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE habits ADD COLUMN repeatWeekdays TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habits ADD COLUMN repeatMonthDays TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habits ADD COLUMN endType INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN repeatTimes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN remindEndDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habits ADD COLUMN firedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN remindStartDate TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 → v4 迁移：分类 / 排序 / 置顶。
 *
 * 老习惯的默认值选择：
 *   - category 默认「生活」——老数据没有分类概念，归进最大众的一类
 *   - sortOrder 默认 0——排序查询是 `sortOrder ASC, createdAt ASC`，
 *     大家都是 0 时自然退回原来的创建顺序，**升级后列表顺序肉眼不变**
 *   - pinned 默认 0（false）
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN category TEXT NOT NULL DEFAULT '生活'")
        db.execSQL("ALTER TABLE habits ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 数据库单例。
 *
 * 为什么必须单例？Room 实例很重（持有数据库连接、线程池），
 * 每用一次 new 一个，轻则卡顿，重则「同一个库被打开多次」出各种诡异锁问题。
 *
 * 双重检查锁（@Volatile + synchronized）是标准写法：
 *   - @Volatile 保证一个线程的写入立刻对其他线程可见
 *   - synchronized 保证并发时只创建一次
 *
 * 为什么用 applicationContext 而不是 Activity 的 context？
 * Activity 被销毁后它的 context 会泄漏，而数据库活得比任何 Activity 都长。
 */
object DatabaseProvider {

    @Volatile
    private var instance: HabitDatabase? = null

    fun get(context: Context): HabitDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HabitDatabase::class.java,
                "daka.db"
            )
                // 开发阶段的兜底开关：找不到迁移路径时直接重建数据库。
                // ⚠️ 代价是**用户数据会被清空**。v2→v3 已经写了正式迁移（MIGRATION_2_3），
                // 这一行只在「降级安装」或「手改过 schema 又没写迁移」的意外情况下生效，
                // 属于宁可清空也不要启动崩溃的最后兜底。
                .fallbackToDestructiveMigration(true)
                // 正式迁移：保住已有数据
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
}
