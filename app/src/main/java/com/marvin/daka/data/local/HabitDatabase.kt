package com.marvin.daka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitRecord
import com.marvin.daka.model.HabitSkip
import com.marvin.daka.model.Reminder

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
    entities = [Habit::class, HabitRecord::class, Reminder::class, HabitSkip::class],
    // version 7：V6 加 reminders 表（附加提醒），V7 加 habit_skips 表（跳过当天）。
    //
    // 演进记录：
    //   v1 → v2：M5 加 colorArgb 列
    //   v2 → v3：V3 加提醒字段组
    //   v3 → v4：V4 加分类/排序/置顶
    //   v4 → v5：V5 加备注
    //   v5 → v6：V6 加 reminders 表（每个习惯可有多条提醒）
    //   v6 → v7：V7 加 habit_skips 表（跳过当天，跳过的不提醒、不断签）
    //
    // ⚠️ 改表结构必须 +1，否则真机上升级会直接崩（Room 会校验 schema 哈希）。
    version = 7,
    exportSchema = false
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun reminderDao(): ReminderDao
    abstract fun habitSkipDao(): HabitSkipDao
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
 * v4 → v5 迁移：备注。
 *
 * 老习惯的 note 默认空字符串——它们本来就没有备注，空着就是最诚实的状态，
 * 不要自作主张填「无」之类的占位文字（界面要判断有没有备注，空串最好判断）。
 *
 * 同样遵守铁律：ADD COLUMN 必须带 NOT NULL DEFAULT，且默认值要和
 * [Habit] 数据类里的默认值（""）保持一致，否则 Room 校验 schema 会报错。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v5 → v6 迁移：新建附加提醒表（reminders）。
 *
 * 一个习惯可以设多条提醒：主提醒留在 habits 表的提醒列上（老结构不动，
 * 老数据零迁移成本），多出来的每条存一行在这里。
 *
 * ⚠️ 建表 SQL 必须和 [Reminder] 实体**逐字段一致**——Room 在迁移后会
 * 校验实际表结构和实体声明是否完全对得上（列名、类型、非空、外键、索引），
 * 少一个列、错一个 NOT NULL 都会让 App 启动即崩。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `reminders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habitId` INTEGER NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `reminderHour` INTEGER NOT NULL,
                `reminderMinute` INTEGER NOT NULL,
                `repeatType` INTEGER NOT NULL,
                `repeatInterval` INTEGER NOT NULL,
                `repeatWeekdays` TEXT NOT NULL,
                `repeatMonthDays` TEXT NOT NULL,
                `endType` INTEGER NOT NULL,
                `repeatTimes` INTEGER NOT NULL,
                `remindEndDate` TEXT NOT NULL,
                `firedCount` INTEGER NOT NULL,
                `remindStartDate` TEXT NOT NULL,
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminders_habitId` ON `reminders` (`habitId`)"
        )
    }
}

/**
 * v6 → v7 迁移：新建跳过当天表（habit_skips）。
 *
 * 一行 = 某习惯某天被用户主动跳过：那天不算完成、也不算断签。
 * (habitId, skipDate) 上建唯一索引，重复点「跳过」不会插出两条。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `habit_skips` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habitId` INTEGER NOT NULL,
                `skipDate` TEXT NOT NULL,
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_skips_habitId_skipDate` "
                + "ON `habit_skips` (`habitId`, `skipDate`)"
        )
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
}
