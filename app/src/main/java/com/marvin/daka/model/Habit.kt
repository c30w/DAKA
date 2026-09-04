package com.marvin.daka.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一个习惯 —— 数据库 habits 表里的一行。
 *
 * 关于字段：
 * - [id] 主键，autoGenerate = true 表示「不用我管，数据库自己递增」。
 *   默认值 0 是配合它的写法：插入时传 0，Room 知道这是新数据、要分配新 id。
 * - [colorArgb] 主题色（ARGB 打包成一个 Long）。M5 新建页里由用户挑选。
 *   给默认值是为了兼容早期没颜色的老数据——**加列必须给默认值**，
 *   否则数据库读老行时会因为「这一列是空的」直接崩。
 * - [createdAt] 创建时间戳，列表用它排序。
 * - [archivedAt] 软删除：不为空表示「已归档」，列表隐藏但不真删，
 *   因为真删会把历史打卡记录一起带走（外键级联）。
 *
 * ⚠️ 这里**没有** streak / doneToday 字段（那是 M1/M2 的老写法）。
 * 它们是从打卡记录实时算出来的，存下来必然和记录对不上。界面要看，用 [HabitUi]。
 *
 * V2 补充：加了 @Serializable，备份时可以直接把它序列化成 JSON。
 *
 * V3 补充：加了「每个习惯独立的提醒」字段组（见文件末尾的说明）。
 */
@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB 主题色，默认 Material 紫 */
    val colorArgb: Long = 0xFF6750A4,
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,

    // ---------- V3：每习惯独立提醒 ----------

    /** 这个习惯要不要单独提醒。默认关——新建习惯不该自作主张给用户发通知 */
    override val reminderEnabled: Boolean = false,

    /** 提醒时间：小时（0-23），默认 21 点 */
    override val reminderHour: Int = 21,

    /** 提醒时间：分钟（0-59） */
    override val reminderMinute: Int = 0,

    /**
     * 重复方式，取值见 [RepeatType.code]。
     * 存 Int 而不是枚举，是因为 Room 默认只能存基础类型；
     * 要存枚举得额外写 TypeConverter，为 6 个值不值得。
     */
    override val repeatType: Int = RepeatType.DAILY.code,

    /** 每 N 天提醒一次（仅 [RepeatType.INTERVAL_DAYS] 用）。1 = 每天 */
    override val repeatInterval: Int = 1,

    /**
     * 每周提醒的星期几，逗号分隔的 ISO 星期数字（周一=1 … 周日=7）。
     * 例："1,3,5" = 周一三五。空字符串 = 没选（此时等同于每天）。
     * 仅 [RepeatType.WEEKLY] 用。
     *
     * 为什么是字符串而不是 List？Room 存不了 List，得写 TypeConverter。
     * 逗号分隔的字符串老土但零成本，数据量和查询复杂度都低到可以忽略。
     */
    override val repeatWeekdays: String = "",

    /** 每月提醒的几号，逗号分隔（例："1,15"）。仅 [RepeatType.MONTHLY] 用 */
    override val repeatMonthDays: String = "",

    /**
     * 结束方式，取值见 [EndType.code]。
     */
    override val endType: Int = EndType.NEVER.code,

    /** [EndType.AFTER_TIMES]：总共提醒多少次后停止。0 = 不限 */
    override val repeatTimes: Int = 0,

    /** [EndType.ON_DATE]：结束日期 "yyyy-MM-dd"，空字符串 = 不限 */
    override val remindEndDate: String = "",

    /**
     * 已经提醒过多少次。配合 [repeatTimes] 判断「该停了」。
     *
     * ⚠️ 这是**唯一一个会被闹钟自己改写的字段**：
     * 每次提醒触发后 +1。别把它当「展示用数据」，它是状态机的一部分。
     */
    override val firedCount: Int = 0,

    /**
     * 提醒生效的起始日期 "yyyy-MM-dd"，默认空 = 从今天算起。
     * [RepeatType.INTERVAL_DAYS] 需要它当锚点：
     * 「每 3 天」是从哪天开始数的，必须有据可依，否则每次重排都会漂。
     */
    override val remindStartDate: String = "",

    // ---------- V4：分类 / 置顶 / 排序 ----------

    /** 分类名（生活/工作/学习/健康/其他）。存字符串而不是外键：分类是枚举不是表，没必要建表 */
    val category: String = HabitCategory.DEFAULT,

    /** 排序号，小的在前。移动位置 = 改两个习惯的这 个值。同号时按创建时间排 */
    val sortOrder: Int = 0,

    /** 是否置顶。置顶的习惯永远排在非置顶前面（分类筛选之内） */
    val pinned: Boolean = false,

    // ---------- V5：备注 ----------

    /**
     * 备注。用户随便写点什么（目标、注意事项、心得……），纯文本不做任何解析。
     *
     * 为什么放在 habits 表里加一列，而不是单独建 note 表？
     * 一个习惯只有一条备注，是一对一关系。单独建表意味着要多维护一个外键、
     * 查询时要联表，而收益是零。加一列 + 一条 ADD COLUMN 迁移就能搞定的事，
     * 别给自己加戏。
     *
     * ⚠️ 加列必须给默认值（这里 ""），否则 Room 读老行时会因「这列是空的」直接崩。
     */
    val note: String = ""
) : ReminderLike {

    /** 主提醒：habitId 就是自己的 id（接口要求的定位字段） */
    override val habitId: Long get() = id

    /** 主提醒的 reminderId 固定 0；reminders 表里的附加提醒用自己的主键 */
    override val reminderId: Long get() = 0

    /** 重复方式枚举。数据库里存的是 Int，读成对象后用这个拿枚举。 */
    override val repeatTypeEnum: RepeatType get() = RepeatType.of(repeatType)

    /** 结束方式枚举 */
    override val endTypeEnum: EndType get() = EndType.of(endType)

    /** 每周星期几的数字集合（1=周一 … 7=周日）。解析失败返回空集合 */
    override val weekdaySet: Set<Int>
        get() = RepeatParse.intList(repeatWeekdays).toSet()

    /** 每月几号的集合。解析失败返回空集合 */
    override val monthDaySet: Set<Int>
        get() = RepeatParse.intList(repeatMonthDays).filter { it in 1..31 }.toSet()

    /** 提醒起始日期。没设就从「创建那天」算 */
    override val effectiveStartDate: String
        get() = remindStartDate.ifBlank {
            java.time.Instant.ofEpochMilli(createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }
}

/** 逗号分隔字符串的解析工具。放在这里是为了让 [Habit] 的属性保持一行一个，不塞逻辑。 */
object RepeatParse {

    /** "1,3,5" → [1, 3, 5]；空串、脏数据 → 空列表（绝不抛异常，脏数据不能让 App 崩） */
    fun intList(raw: String): List<Int> =
        raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
}

/**
 * 习惯分类。
 *
 * V4.11 之前是写死的 5 个内置枚举 + 一个「其他」兜底，不允许用户自建——
 * 当时认为自建要加表、加管理页，收益配不上成本。
 *
 * V4.11 起开放自建：分类不再有「其他」这个收容所，用户可以在新建/编辑页
 * 直接输入任意分类名。分类本质是 [Habit.category] 这个字符串，没有独立表，
 * 所以「新建分类」= 把这个字符串写进习惯，首页分组逻辑会自动把它当成一个新分组冒出来，
 * 零迁移成本。
 *
 * 这里只保留 4 个常用的内置分类做快捷 chip，其余全是用户自由输入。
 */
object HabitCategory {
    const val LIFE = "生活"
    const val WORK = "工作"
    const val STUDY = "学习"
    const val HEALTH = "健康"

    const val DEFAULT = LIFE

    /** 内置分类，按固定顺序展示成快捷 chip。自定义分类走输入框，不在这里 */
    val ALL = listOf(LIFE, WORK, STUDY, HEALTH)

    /** 是否内置分类（UI 用 chip 展示的就是这些；自定义分类走输入框） */
    fun isBuiltin(raw: String): Boolean = raw in ALL

    /**
     * 脏数据兜底：空白/非法 → 默认「生活」。
     * 非空原样保留——包括自定义分类，V4.11 起不再往「其他」里塞。
     */
    fun of(raw: String): String = raw.trim().ifBlank { DEFAULT }
}
