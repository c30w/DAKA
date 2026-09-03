package com.marvin.daka.ui.common

/**
 * 图标目录 —— 新建/编辑习惯页的 emoji 全集 + 按名称自动推荐。
 *
 * 为什么继续用 emoji 而不是 Material 图标？
 *   1. emoji 彩色、有表情，当「习惯」图标比单色矢量图标生动
 *   2. 零依赖零体积，不用引入 icons-extended 那个几 MB 的包
 *   3. 老数据里存的就是 emoji，换图标体系还得做迁移
 *
 * 自动推荐：按习惯名称里的关键词匹配。匹配不到就按「第一个图标」兜底——
 * 推荐只是起点，用户随时可以改，所以宁可朴素也别猜错得太离谱。
 */
object IconCatalog {

    /** 一个图标 = emoji + 中文名（读屏用，选择对话框里也显示） */
    data class IconItem(val emoji: String, val label: String)

    /** 全量图标。新建页默认展示前 11 个 + 1 个「更多」入口（V5 起 2 行），其余进二级对话框 */
    val ALL: List<IconItem> = listOf(
        IconItem("💧", "喝水"),
        IconItem("🏃", "跑步"),
        IconItem("📖", "读书"),
        IconItem("🧘", "冥想"),
        IconItem("💤", "睡觉"),
        IconItem("🥗", "饮食"),
        IconItem("💪", "健身"),
        IconItem("✍️", "写作"),
        IconItem("🧹", "打扫"),
        IconItem("☀️", "早起"),
        IconItem("🦷", "刷牙"),
        IconItem("💊", "吃药"),
        IconItem("🎯", "专注"),
        IconItem("💻", "编程"),
        IconItem("🎸", "练琴"),
        IconItem("🌱", "养植物"),
        IconItem("💰", "记账"),
        // ---- 以下在「更多」对话框里 ----
        IconItem("🚭", "戒烟"),
        IconItem("🚶", "散步"),
        IconItem("🚴", "骑行"),
        IconItem("🏊", "游泳"),
        IconItem("🤸", "拉伸"),
        IconItem("☕", "咖啡"),
        IconItem("🍵", "喝茶"),
        IconItem("🥛", "牛奶"),
        IconItem("🍎", "水果"),
        IconItem("🍳", "做饭"),
        IconItem("🌅", "看日出"),
        IconItem("🌙", "早睡"),
        IconItem("📚", "学习"),
        IconItem("🗣️", "口语"),
        IconItem("🔤", "背单词"),
        IconItem("📝", "日记"),
        IconItem("🎨", "绘画"),
        IconItem("🧩", "益智"),
        IconItem("🎹", "乐器"),
        IconItem("🎤", "唱歌"),
        IconItem("📷", "拍照"),
        IconItem("🌸", "浇花"),
        IconItem("🐶", "遛狗"),
        IconItem("🧴", "护肤"),
        IconItem("🕐", "守时"),
        IconItem("📵", "少刷手机"),
        IconItem("🚰", "泡脚"),
        IconItem("📞", "联系家人"),
        IconItem("🛒", "采购"),
        IconItem("🧾", "复盘"),
        IconItem("⭐", "自定义")
    )

    /** 新建页默认展示的图标（不含「更多」入口），3 行 × 6 列 - 1 = 17 个 */
    val FEATURED: List<IconItem> = ALL.take(17)

    /**
     * 按习惯名称推荐一个图标。
     *
     * 关键词按「越具体越优先」排列：先匹配双字/多字词（「背单词」），
     * 再匹配单字（「书」）。遍历顺序即优先级，命中即返回。
     */
    fun suggest(name: String): String {
        if (name.isBlank()) return FEATURED.first().emoji
        KEYWORD_MAP.forEach { (emoji, keywords) ->
            if (keywords.any { name.contains(it) }) return emoji
        }
        return FEATURED.first().emoji
    }

    /** emoji → 中文名。选择器网格与对话框共用 */
    val LABELS: Map<String, String> = ALL.associate { it.emoji to it.label }

    /**
     * 关键词表。刻意放在 ALL 之后定义：只引用 emoji 字符串，不依赖列表顺序，
     * 往 ALL 里加图标不会影响这里的匹配。
     */
    private val KEYWORD_MAP: List<Pair<String, List<String>>> = listOf(
        "💧" to listOf("喝水", "饮水", "水"),
        "🥛" to listOf("牛奶", "奶"),
        "☕" to listOf("咖啡"),
        "🍵" to listOf("喝茶", "泡茶", "茶"),
        "💊" to listOf("吃药", "服药", "药"),
        "🏃" to listOf("跑步", "慢跑", "晨跑", "夜跑", "跑"),
        "🚶" to listOf("散步", "走路", "步行"),
        "🚴" to listOf("骑行", "骑车", "单车"),
        "🏊" to listOf("游泳"),
        "🤸" to listOf("拉伸", "热身", "体态"),
        "💪" to listOf("健身", "锻炼", "力量", "俯卧撑", "仰卧", "深蹲", "练"),
        "🧘" to listOf("冥想", "打坐", "呼吸", "正念"),
        "💤" to listOf("睡觉", "睡眠", "就寝"),
        "🌙" to listOf("早睡", "熬夜"),
        "🌅" to listOf("早起", "日出", "起床"),
        "🦷" to listOf("刷牙", "牙"),
        "🧴" to listOf("护肤", "洗脸", "防晒"),
        "🚰" to listOf("泡脚"),
        "🥗" to listOf("饮食", "吃饭", "早餐", "午餐", "晚餐", "轻食", "沙拉", "戒糖"),
        "🍎" to listOf("水果", "吃果"),
        "🍳" to listOf("做饭", "烹饪", "下厨"),
        "📖" to listOf("读书", "阅读", "看书", "书"),
        "📚" to listOf("学习", "复习", "功课", "课程"),
        "🔤" to listOf("背单词", "单词", "外语"),
        "🗣️" to listOf("口语", "发音", "朗读", "听力"),
        "✍️" to listOf("写作", "写", "码字"),
        "📝" to listOf("日记", "记录", "随笔"),
        "🧾" to listOf("复盘", "总结", "周报"),
        "🎯" to listOf("专注", "番茄", "集中"),
        "💻" to listOf("编程", "代码", "敲代码", "开发"),
        "🎸" to listOf("吉他", "练琴"),
        "🎹" to listOf("钢琴", "键盘", "乐器"),
        "🎤" to listOf("唱歌", "声乐", "开嗓"),
        "🎨" to listOf("绘画", "画画", "素描"),
        "🧩" to listOf("拼图", "益智", "数独"),
        "📷" to listOf("拍照", "摄影", "照片"),
        "🌱" to listOf("养植物", "植物", "绿植"),
        "🌸" to listOf("浇花", "花"),
        "🐶" to listOf("遛狗", "狗", "猫", "宠物", "喂"),
        "🧹" to listOf("打扫", "清洁", "收拾", "整理", "家务"),
        "🛒" to listOf("采购", "购物", "买菜"),
        "💰" to listOf("记账", "存钱", "理财", "钱"),
        "📵" to listOf("少刷手机", "戒手机", "不刷", "手机"),
        "🚭" to listOf("戒烟", "烟"),
        "📞" to listOf("联系家人", "打电话", "父母", "回家"),
        "🕐" to listOf("守时", "准时", "不迟到"),
        "⭐" to listOf("自定义")
    )
}
