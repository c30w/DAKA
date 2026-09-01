package com.marvin.daka.ui.template

import androidx.annotation.StringRes
import com.marvin.daka.R
import com.marvin.daka.model.HabitCategory

/**
 * 一个「习惯模板」——别人（或未来的你）验证过值得坚持的事，点一下就能加进列表。
 *
 * 为什么需要模板？新建习惯最大的门槛不是操作，是**决策成本**：
 * 要名字、要图标、要颜色、要分类、还想个提醒时间。五步走完，
 * 想养成的冲动已经凉了半截。模板把这五步压成「点一下」。
 *
 * 设计取舍：
 * - **只有 12 个，不做成可编辑的模板库**。自用 App 里，模板编辑界面
 *   的使用频率远低于使用模板本身，为它建表、建页面不划算。
 *   真想加模板，改这个文件里的 [ALL] 一行即可。
 * - **提醒默认关**（[reminderEnabled] = false）。模板只在卡片上显示
 *   「建议 X:XX 提醒」，不替用户开通知——App 不该在用户没点头前就每天响。
 *   这是项目从 V3 起就有的原则，别破例。
 *
 * @param emoji 图标。全部取自 [com.marvin.daka.ui.common.IconCatalog.ALL]，
 *   保证用户在图标选择器里也能找到同一个
 * @param colorArgb 主题色。取自 [com.marvin.daka.ui.common.ColorCatalog.ALL]
 * @param suggestHour / [suggestMinute] 建议提醒时间，仅用于卡片展示和导入后的预填
 * @param nameRes / [descRes] 文案走 strings.xml，跟随系统语言（中/英）
 */
data class HabitTemplate(
    val emoji: String,
    val colorArgb: Long,
    val category: String,
    val suggestHour: Int,
    val suggestMinute: Int = 0,
    // 显式写成 @param：data class 主构造的 val 既是参数也是属性/字段，
    // 不指定 use-site target 的话 Kotlin 会警告「以后可能同时应用到 field」
    @param:StringRes val nameRes: Int,
    @param:StringRes val descRes: Int
)

/**
 * 内置模板库。
 *
 * 排序 = 展示顺序，按分类聚合后展示，每组内保持这里的先后。
 * 选品标准：**高频 + 门槛低 + 反馈快**——都是「今天做了今天就能感受到」的事，
 * 那些「坚持三年才见效」的（比如存钱）不在这里添堵。
 */
object HabitTemplates {

    val ALL: List<HabitTemplate> = listOf(
        // ---- 健康 ----
        HabitTemplate(
            emoji = "☀️", colorArgb = 0xFFFFC107, category = HabitCategory.HEALTH,
            suggestHour = 7,
            nameRes = R.string.tpl_wake_early_name, descRes = R.string.tpl_wake_early_desc
        ),
        HabitTemplate(
            emoji = "💧", colorArgb = 0xFF2196F3, category = HabitCategory.HEALTH,
            suggestHour = 10,
            nameRes = R.string.tpl_drink_water_name, descRes = R.string.tpl_drink_water_desc
        ),
        HabitTemplate(
            emoji = "🏃", colorArgb = 0xFF4CAF50, category = HabitCategory.HEALTH,
            suggestHour = 19,
            nameRes = R.string.tpl_exercise_name, descRes = R.string.tpl_exercise_desc
        ),
        HabitTemplate(
            emoji = "🌙", colorArgb = 0xFF3F51B5, category = HabitCategory.HEALTH,
            suggestHour = 22, suggestMinute = 30,
            nameRes = R.string.tpl_sleep_early_name, descRes = R.string.tpl_sleep_early_desc
        ),
        HabitTemplate(
            emoji = "🧘", colorArgb = 0xFF00BCD4, category = HabitCategory.HEALTH,
            suggestHour = 8,
            nameRes = R.string.tpl_meditation_name, descRes = R.string.tpl_meditation_desc
        ),
        HabitTemplate(
            emoji = "🤸", colorArgb = 0xFFFF9800, category = HabitCategory.HEALTH,
            suggestHour = 9,
            nameRes = R.string.tpl_stretching_name, descRes = R.string.tpl_stretching_desc
        ),
        HabitTemplate(
            emoji = "💊", colorArgb = 0xFFF44336, category = HabitCategory.HEALTH,
            suggestHour = 20,
            nameRes = R.string.tpl_medication_name, descRes = R.string.tpl_medication_desc
        ),

        // ---- 学习 ----
        HabitTemplate(
            emoji = "📖", colorArgb = 0xFF6750A4, category = HabitCategory.STUDY,
            suggestHour = 21, suggestMinute = 30,
            nameRes = R.string.tpl_reading_name, descRes = R.string.tpl_reading_desc
        ),
        HabitTemplate(
            emoji = "🔤", colorArgb = 0xFF536DFE, category = HabitCategory.STUDY,
            suggestHour = 8, suggestMinute = 30,
            nameRes = R.string.tpl_english_name, descRes = R.string.tpl_english_desc
        ),
        HabitTemplate(
            emoji = "📝", colorArgb = 0xFF795548, category = HabitCategory.STUDY,
            suggestHour = 22, suggestMinute = 15,
            nameRes = R.string.tpl_journal_name, descRes = R.string.tpl_journal_desc
        ),

        // ---- 生活 ----
        HabitTemplate(
            emoji = "💰", colorArgb = 0xFF8BC34A, category = HabitCategory.LIFE,
            suggestHour = 22,
            nameRes = R.string.tpl_budgeting_name, descRes = R.string.tpl_budgeting_desc
        ),
        HabitTemplate(
            emoji = "🧴", colorArgb = 0xFFE91E63, category = HabitCategory.LIFE,
            suggestHour = 21,
            nameRes = R.string.tpl_skincare_name, descRes = R.string.tpl_skincare_desc
        )
    )

    /**
     * 按分类分组，组间顺序固定为「健康 → 学习 → 生活 → 工作」，
     * 组内保持 [ALL] 的先后。分类都是内置的，不需要处理自定义分类。
     */
    fun groupByCategory(): List<Pair<String, List<HabitTemplate>>> {
        val order = listOf(HabitCategory.HEALTH, HabitCategory.STUDY, HabitCategory.LIFE, HabitCategory.WORK)
        return order.mapNotNull { cat ->
            val list = ALL.filter { it.category == cat }
            if (list.isEmpty()) null else cat to list
        }
    }
}
