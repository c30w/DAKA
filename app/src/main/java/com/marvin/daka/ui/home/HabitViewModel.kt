package com.marvin.daka.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marvin.daka.calendar.CalendarEvent
import com.marvin.daka.calendar.CalendarRepository
import com.marvin.daka.calendar.endOfDayMillis
import com.marvin.daka.calendar.startOfDayMillis
import com.marvin.daka.data.AppPrefs
import com.marvin.daka.data.local.HabitDao
import com.marvin.daka.data.local.HabitRecordDao
import com.marvin.daka.model.BACKUP_VERSION
import com.marvin.daka.model.BackupData
import com.marvin.daka.model.Habit
import com.marvin.daka.model.HabitCategory
import com.marvin.daka.model.HabitRecord
import com.marvin.daka.model.HabitUi
import com.marvin.daka.model.ReminderConfig
import com.marvin.daka.model.ReminderOccurrence
import com.marvin.daka.reminder.ReminderRule
import com.marvin.daka.reminder.ReminderScheduler
import com.marvin.daka.util.calcStreak
import com.marvin.daka.util.last7Days
import com.marvin.daka.util.todayString
import com.marvin.daka.widget.refreshHabitWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 首页的 ViewModel —— M4 的核心。
 *
 * ViewModel 是干什么的？一句话：**把「数据从哪来、怎么变」从界面里拿走。**
 *
 * M3 的写法是把 DAO 直接传给 HomeScreen，在 Composable 里 collect、里算 streak、
 * 里 launch 协程写库。能跑，但有三个致命问题：
 *   1. 转屏 Activity 重建，Composable 里的计算全部重来
 *   2. 逻辑混在 UI 里，没法单独测试
 *   3. 以后「编辑习惯」「归档」等第二个页面想复用同一份数据，拿不到
 *
 * ViewModel 的生命周期比 Activity 长（转屏不销毁），数据活在里面，UI 只负责画。
 *
 * 三个关键点：
 *
 * 1. **combine**：把「习惯列表」和「打卡记录」两个 Flow 合成一个。
 *    任意一边变化，就重新算一遍，产出新的 HabitUi 列表。
 *
 * 2. **stateIn**：把冷 Flow 转成 StateFlow（有「当前值」的热流）。
 *    WhileSubscribed(5000) = 没人看了就等 5 秒再停，避免转屏瞬间断流重连。
 *
 * 3. **viewModelScope**：ViewModel 专用的协程作用域，ViewModel 销毁时自动取消。
 *    用它就不用自己管 rememberCoroutineScope 和取消逻辑了。
 *
 * V3 补充：这个 ViewModel 现在还承担「提醒调度」和「日历展开」。
 * 它们都需要同一份 habits 数据、都跨页面共享（首页 / 设置 / 日历），
 * 拆成三个 ViewModel 反而要处理三者之间同步数据的问题，得不偿失。
 *
 * 关于 Context：只持有 **applicationContext**。
 * Activity 的 context 持有 Activity 引用，ViewModel 活得比 Activity 长，
 * 存下来就是经典的「Activity 泄漏」——转屏几次之后内存里堆着好几个 Activity。
 */
class HabitViewModel(
    private val habitDao: HabitDao,
    private val recordDao: HabitRecordDao,
    private val appContext: Context
) : ViewModel() {

    /**
     * V4.1：首页按分类分组展示，不再用筛选 chips。
     *
     * [sections] 是界面的唯一数据源：分类顺序 = 用户拖出来的顺序（存 AppPrefs），
     * 没自定义过就按内置顺序；每个分组里的习惯按 sortOrder 排。
     *
     * ⚠️ appPrefs 与 items 一样，必须声明在引用它的 Flow 之前（按声明顺序初始化）。
     */
    private val appPrefs = AppPrefs(appContext)

    /** 界面订阅这个就行 —— 数据齐了、状态算好了，直接画 */
    val items: StateFlow<List<HabitUi>> =
        combine(
            habitDao.observeAll(),
            recordDao.observeAll()
        ) { habits, records ->
            buildHabitUiList(habits, records)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 首页分组数据：有序的分类 + 每类里排好序的习惯 */
    val sections: StateFlow<List<HomeSection>> =
        combine(
            habitDao.observeAll(),
            recordDao.observeAll(),
            appPrefs.categoryOrder
        ) { habits, records, savedOrder ->
            val byCategory = habits.groupBy { HabitCategory.of(it.category) }
            if (byCategory.isEmpty()) return@combine emptyList()

            // 分类顺序：用户拖过的（savedOrder）在前，库里新出现的分类补在后面。
            // 「新出现的」按内置顺序插队尾，保证首次使用时 5 个分类的顺序可预期
            val present = byCategory.keys
            // 内置分类按 HabitCategory.ALL 顺序；自定义分类（不在 ALL 里）统一排到末尾，
            // 彼此按名称排，保证顺序稳定、不挤进内置分类中间。
            val orderedCategories =
                savedOrder.filter { it in present } +
                    (present - savedOrder.toSet()).sortedWith(
                        compareBy<String>(
                            { if (it in HabitCategory.ALL) HabitCategory.ALL.indexOf(it) else Int.MAX_VALUE },
                            { it }
                        )
                    )

            orderedCategories.map { category ->
                HomeSection(
                    category = category,
                    habits = buildHabitUiList(
                        // 分组内排序：sortOrder 小的在前，同号按创建时间（老数据全是 0 的兜底）
                        byCategory.getValue(category)
                            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt })),
                        records
                    )
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 原始的习惯列表（含提醒设置）。
     *
     * 为什么还要单独来一份，而不复用 [items]？
     * 因为 [HabitUi] 是**给首页卡片看的**，它丢掉了 colorArgb 之外的所有提醒字段。
     * 设置页要列「每个习惯的提醒时间」、日历页要展开重复规则，都需要原始对象。
     * 与其为了省一个 Flow 把提醒字段塞进 HabitUi（让卡片背上一堆用不上的数据），
     * 不如多一个 Flow，各取所需。
     */
    val habits: StateFlow<List<Habit>> = habitDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ------------------------------------------------------------------
    // V3：日历视图
    // ------------------------------------------------------------------

    /**
     * 当前日历显示的月份。用该月 1 号作为锚点，而不是 YearMonth——
     * YearMonth 在某些低端 ROM 的 java.time 实现上有兼容问题，LocalDate 最稳。
     */
    private val _monthAnchor = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val monthAnchor: StateFlow<LocalDate> = _monthAnchor.asStateFlow()

    /**
     * 本月（含前后补齐的整周）所有习惯提醒，按时间升序。
     *
     * 每次切月或习惯设置变更都会重算——规则展开 [ReminderRule.expand]
     * 是纯内存计算，几十个习惯 × 42 天，耗时可以忽略。
     */
    val reminderOccurrences: StateFlow<List<ReminderOccurrence>> =
        combine(habits, _monthAnchor) { list, anchor ->
            val (from, to) = monthGridRange(anchor)
            list.flatMap { habit ->
                val ruleText = ReminderRule.describe(habit)
                ReminderRule.expand(habit, from, to).map { date ->
                    ReminderOccurrence(
                        habitId = habit.id,
                        habitName = habit.name,
                        emoji = habit.emoji,
                        colorArgb = habit.colorArgb,
                        date = date,
                        hour = habit.reminderHour,
                        minute = habit.reminderMinute,
                        ruleText = ruleText
                    )
                }
            }.sortedWith(compareBy({ it.date }, { it.hour }, { it.minute }))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 从手机系统日历读到的日程。没授权就是空列表 */
    private val _systemEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val systemEvents: StateFlow<List<CalendarEvent>> = _systemEvents.asStateFlow()

    /** 是否已拿到读日历权限。界面据此决定要不要显示「去授权」引导 */
    private val _calendarReadable = MutableStateFlow(false)
    val calendarReadable: StateFlow<Boolean> = _calendarReadable.asStateFlow()

    /** 翻月。delta = +1 下一月，-1 上一月 */
    fun goMonth(delta: Long) {
        _monthAnchor.value = _monthAnchor.value.plusMonths(delta).withDayOfMonth(1)
        refreshSystemEvents()
    }

    /** 跳回本月 */
    fun goToday() {
        _monthAnchor.value = LocalDate.now().withDayOfMonth(1)
        refreshSystemEvents()
    }

    /**
     * 读取系统日历在当前显示月份的日程。
     *
     * 为什么把 Context 当参数传进来、而不是构造时存下来？
     * 因为这里是**一次性使用**（查完就扔），存下来就得小心泄漏。
     * ContentResolver 查询必须在 IO 线程——主线程查 ContentProvider 会卡 UI。
     */
    fun refreshSystemEvents(context: Context = appContext) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = CalendarRepository(context)
            if (!repo.hasReadPermission()) {
                _calendarReadable.value = false
                _systemEvents.value = emptyList()
                return@launch
            }
            _calendarReadable.value = true

            val (from, to) = monthGridRange(_monthAnchor.value)
            _systemEvents.value = repo.eventsInRange(from.startOfDayMillis(), to.endOfDayMillis())
        }
    }

    // ------------------------------------------------------------------
    // 打卡 / 增删
    // ------------------------------------------------------------------

    /**
     * 打卡 / 取消打卡。
     *
     * 切到 IO 线程执行（主线程读写 Room 会崩）。
     * 注意这里**不手动刷新列表**：写库后 Room 会自动重跑上面两个查询，
     * combine 收到新值 → items 更新 → 界面刷新。这就是「单一数据源」的威力。
     */
    fun toggle(habitId: Long, currentlyDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyDone) {
                recordDao.deleteByDate(habitId, todayString())
            } else {
                recordDao.insert(HabitRecord(habitId = habitId, date = todayString()))
            }
            // 数据变了，立刻推刷新桌面三种小组件（不依赖界面是否在前台）
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * 删除（归档）一个习惯。
     *
     * 走的是软删除：打上 archivedAt 标记，首页列表立刻不再出现它，
     * 但它的历史打卡记录仍然留在库里（备份导出时还会带上）。
     * 这样误删了还有救，也避免了外键级联把历史数据一起带走。
     *
     * V3：删完要顺手**取消它的闹钟**，否则会继续收到一个已经不存在的习惯的提醒。
     */
    fun deleteHabit(habitId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            habitDao.archive(habitId)
            ReminderScheduler.cancelHabit(appContext, habitId)
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * 新建一个习惯。
     *
     * V3：多了提醒配置。插入之后要**重新查一遍**拿到数据库分配的 id，
     * 才能给它排闹钟——id 是闹钟 PendingIntent 的区分依据，不知道 id 就排不了。
     *
     * V4：多了分类。sortOrder 取当前最大值 +1，新习惯排在同分类的最后——
     * 「新来的站队尾」是最不容易让用户困惑的位置。
     */
    fun createHabit(
        name: String,
        emoji: String,
        colorArgb: Long,
        reminder: ReminderConfig,
        category: String = HabitCategory.DEFAULT
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val nextOrder = (habitDao.getMaxSortOrder() ?: 0) + 1
            val ids = habitDao.insertAll(
                listOf(
                    Habit(
                        name = cleanName,
                        emoji = emoji,
                        colorArgb = colorArgb,
                        reminderEnabled = reminder.enabled,
                        reminderHour = reminder.hour,
                        reminderMinute = reminder.minute,
                        repeatType = reminder.repeatType.code,
                        repeatInterval = reminder.interval.coerceAtLeast(1),
                        repeatWeekdays = reminder.weekdays.sorted().joinToString(","),
                        repeatMonthDays = reminder.monthDays.sorted().joinToString(","),
                        endType = reminder.endType.code,
                        repeatTimes = reminder.times,
                        remindEndDate = reminder.endDate,
                        remindStartDate = reminder.startDate.ifBlank { todayString() },
                        category = category,
                        sortOrder = nextOrder
                    )
                )
            )
            val newId = ids.firstOrNull() ?: return@launch
            habitDao.getById(newId)?.let { applyReminder(it) }
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * V4：整体编辑一个习惯（名称/图标/颜色/分类/提醒）。
     *
     * 做法：先从库里捞最新值，copy 改动字段再整行写回。
     * 不用「界面传上来的旧对象」当基底——那中间可能已经被闹钟改过 firedCount，
     * 拿旧对象写回会把计数抹掉，这是并发覆盖的隐蔽 bug。
     */
    fun updateHabit(
        habitId: Long,
        name: String,
        emoji: String,
        colorArgb: Long,
        category: String,
        reminder: ReminderConfig
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val current = habitDao.getById(habitId) ?: return@launch
            habitDao.update(
                current.copy(
                    name = cleanName,
                    emoji = emoji,
                    colorArgb = colorArgb,
                    category = category,
                    // 提醒字段随编辑页一起更新；改了规则，触发计数归零
                    reminderEnabled = reminder.enabled,
                    reminderHour = reminder.hour,
                    reminderMinute = reminder.minute,
                    repeatType = reminder.repeatType.code,
                    repeatInterval = reminder.interval.coerceAtLeast(1),
                    repeatWeekdays = reminder.weekdays.sorted().joinToString(","),
                    repeatMonthDays = reminder.monthDays.sorted().joinToString(","),
                    endType = reminder.endType.code,
                    repeatTimes = reminder.times,
                    remindEndDate = reminder.endDate,
                    remindStartDate = reminder.startDate.ifBlank { current.effectiveStartDate },
                    firedCount = 0
                )
            )
            habitDao.getById(habitId)?.let { applyReminder(it) }
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * 置顶 / 取消置顶。
     *
     * V4.1 语义更新：置顶 = 打上图钉标记 + **跳到所在分类的顶部**。
     * 为什么不靠 SQL 的 pinned DESC 排序？因为分类内的顺序现在是用户拖出来的
     * （sortOrder 说了算），再让置顶强行插队就会出现「拖了没反应」的灵异现象——
     * 显示顺序和 sortOrder 对不上，拖拽的换位计算全会错位。
     * 统一「一个顺序来源」（sortOrder）是拖拽排序能成立的前提。
     */
    fun togglePin(habitId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitDao.getById(habitId) ?: return@launch
            if (habit.pinned) {
                habitDao.setPinned(habitId, false)
            } else {
                pinToCategoryTop(habit)
            }
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * V4.2：只置顶、不取消。
     *
     * 长按拖到顶部「置顶区」松手走这个——用户把卡片拖进置顶区的意图只有一个，
     * 如果沿用 togglePin 的开关语义，已置顶的习惯拖进置顶区反而会变成取消置顶，
     * 反直觉。所以拖投放区一律理解为「置顶」，取消置顶走右滑或操作面板。
     */
    fun pinToTop(habitId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitDao.getById(habitId) ?: return@launch
            if (!habit.pinned) pinToCategoryTop(habit)
            refreshHabitWidgets(appContext)
        }
    }

    /** 置顶的核心动作：排到分类最前（sortOrder 取分类内最小值 -1）+ 打图钉标记 */
    private suspend fun pinToCategoryTop(habit: Habit) {
        val minOrder = categoryMembers(habit.category).minOf { it.sortOrder }
        habitDao.setSortOrder(habit.id, minOrder - 1)
        habitDao.setPinned(habit.id, true)
    }

    /**
     * 上移 / 下移一位（操作面板里的按钮）。
     *
     * @param habitId 要移动的习惯
     * @param delta -1 = 往前（上移），+1 = 往后（下移）
     *
     * 只在**同一个分类内**移动——分类是用户心智里的「文件夹」，
     * 上下移动跨了文件夹反而让人困惑。跨分类挪动请用编辑页改分类。
     */
    fun moveHabit(habitId: Long, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitDao.getById(habitId) ?: return@launch
            val members = categoryMembers(habit.category).toMutableList()
            val index = members.indexOfFirst { it.id == habitId }
            val target = index + delta
            if (index < 0 || target < 0 || target >= members.size) return@launch

            members.apply { add(target, removeAt(index)) }
            assignSectionOrder(members)
            refreshHabitWidgets(appContext)
        }
    }

    /**
     * V4.3：拖拽松手后**一次性落库**。
     *
     * V4.2 的做法是「拖动中每换一次位就写一次库」，UI 手感差的全在这：
     * 写库 → Flow 推送 → 重组 → 重新测量，一个来回几十毫秒，连续拖必然卡顿。
     * 现在拖动全程只改内存里的列表（界面层负责），松手才把最终结果整包写进来：
     *   - 分类顺序 → AppPrefs（分类不是表，没有地方挂顺序列，见 [AppPrefs]）
     *   - 每个习惯的 category + sortOrder → 一次 UPDATE 一个，sortOrder = 组内下标
     *
     * sections 里每个分组的 habits 顺序就是最终显示顺序。
     */
    fun applyOrder(sections: List<HomeSection>) {
        viewModelScope.launch(Dispatchers.IO) {
            appPrefs.setCategoryOrder(sections.map { it.category })
            sections.forEach { section ->
                section.habits.forEachIndexed { index, habit ->
                    // 拖拽可能把习惯带进了别的分组，category 变了要单独写
                    if (HabitCategory.of(habit.category) != HabitCategory.of(section.category)) {
                        habitDao.setCategory(habit.id, section.category)
                    }
                    habitDao.setSortOrder(habit.id, index)
                }
            }
            refreshHabitWidgets(appContext)
        }
    }

    /** 某个分类下的全部未归档习惯，按显示顺序（sortOrder → createdAt）排好 */
    private suspend fun categoryMembers(category: String): List<Habit> =
        habitDao.getAllActive()
            .filter { HabitCategory.of(it.category) == HabitCategory.of(category) }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))

    /** 分组内重排序号：把显示顺序固化成 sortOrder = 0..n-1 */
    private suspend fun assignSectionOrder(members: List<Habit>) {
        members.forEachIndexed { index, habit ->
            if (habit.sortOrder != index) habitDao.setSortOrder(habit.id, index)
        }
    }

    /**
     * 更新一个习惯的提醒设置。
     *
     * 三件事必须一起做，少一件都会出问题：
     *   1. 写库（持久化设置）
     *   2. 清掉已触发次数（用户改了规则，旧计数不该继承，
     *      否则「提醒 10 次」在第 8 次时改成「提醒 5 次」会立刻变成已结束）
     *   3. 重排闹钟（否则要等旧闹钟触发后才生效，用户会以为没改成功）
     */
    fun updateReminder(habitId: Long, reminder: ReminderConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            habitDao.updateReminder(
                id = habitId,
                enabled = reminder.enabled,
                hour = reminder.hour,
                minute = reminder.minute,
                repeatType = reminder.repeatType.code,
                interval = reminder.interval.coerceAtLeast(1),
                weekdays = reminder.weekdays.sorted().joinToString(","),
                monthDays = reminder.monthDays.sorted().joinToString(","),
                endType = reminder.endType.code,
                times = reminder.times,
                endDate = reminder.endDate,
                startDate = reminder.startDate.ifBlank { todayString() },
                firedCount = 0
            )
            habitDao.getById(habitId)?.let { applyReminder(it) }
            refreshHabitWidgets(appContext)
        }
    }

    /** 按习惯当前设置重排闹钟。关着的会顺带取消旧闹钟 */
    private fun applyReminder(habit: Habit) {
        ReminderScheduler.scheduleHabit(appContext, habit)
    }

    /** 全部习惯重新排一次闹钟。开机、批量变更后用 */
    fun rescheduleAllReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.rescheduleAll(appContext)
        }
    }

    // ---------------- V2：备份导出 / 导入 ----------------

    /**
     * 生成备份文件的 JSON 内容。
     *
     * 这里**故意不碰文件和 Uri**——ViewModel 拿 Context 容易内存泄漏。
     * 它只负责「把数据库内容变成一串 JSON」，写文件由界面层用系统文件选择器（SAF）完成。
     * 职责分开的好处：这段逻辑可以脱离 Android 单独测试，界面换了我不用改。
     */
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val data = BackupData(
            habits = habitDao.getAllForBackup(),
            records = recordDao.getAllForBackup()
        )
        PRETTY_JSON.encodeToString(data)
    }

    /**
     * V4.2：恢复前**只读不写**地解析备份文件，给出预览摘要。
     *
     * 为什么不直接恢复？恢复是要改库的操作，用户应该先看到
     * 「这份备份里有几个习惯、什么时候导出的」再决定确认与否。
     * 这里只解码 JSON、不碰 DAO，解析失败会抛异常，由界面层 catch 后展示。
     */
    suspend fun inspectBackup(json: String): BackupSummary = withContext(Dispatchers.IO) {
        val data = LENIENT_JSON.decodeFromString<BackupData>(json)
        require(data.version <= BACKUP_VERSION) {
            "备份文件来自更新的版本（v${data.version}），请先升级 App"
        }
        BackupSummary(
            habitCount = data.habits.size,
            recordCount = data.records.size,
            exportedAt = data.exportedAt,
            json = json
        )
    }

    /**
     * 从备份 JSON 恢复数据。
     *
     * 采用**合并**策略（同 id 覆盖，不同 id 保留），而不是「清空后重建」：
     * 自用场景下用户多半是「换手机后把老数据并进来」，清空重建会丢掉恢复之后新打的记录。
     *
     * @param json 备份文件内容
     * @return 本次恢复的习惯条数，给界面显示「恢复了 N 个习惯」用
     * @throws IllegalArgumentException 备份文件损坏或版本不认识时抛出，由界面层 catch 后提示用户
     */
    suspend fun restoreFromBackup(json: String): Int = withContext(Dispatchers.IO) {
        // ignoreUnknownKeys：以后备份里多了新字段，老版本 App 也能读（忽略不认识的字段）
        val data = LENIENT_JSON.decodeFromString<BackupData>(json)

        require(data.version <= BACKUP_VERSION) {
            "备份文件来自更新的版本（v${data.version}），请先升级 App"
        }

        // ⚠️ 顺序不能反：records 有指向 habits 的外键，
        // 先插记录会因为「找不到主人」违反外键约束而崩。
        habitDao.upsertAll(data.habits)
        recordDao.upsertAll(data.records)

        // 恢复进来的习惯可能带着提醒设置，全部重排一遍
        ReminderScheduler.rescheduleAll(appContext)
        // 习惯/记录都变了，刷新桌面小组件
        refreshHabitWidgets(appContext)

        data.habits.size
    }
}

/**
 * 两个 Json 实例提到顶层复用。
 *
 * 为什么不能每次用的时候临时 `Json { ... }` 建一个？
 * 因为 Json 的构造要扫描全部序列化器的元数据，开销不小，
 * 在导出/导入这种可能跑在后台的场景里反复建属于白浪费。
 * Kotlin 官方也把它标成了警告。
 */
private val PRETTY_JSON = Json { prettyPrint = true }
private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

/**
 * 首页的一个分类分组：分类标题 + 该分类下排好序的习惯。
 *
 * 界面按 sections 的顺序画分组标题，每个分组里再画习惯卡片——
 * 「分类也是列表的一部分」而不是「筛选器」，这是 V4.1 的核心交互变化。
 */
data class HomeSection(
    val category: String,
    val habits: List<HabitUi>
)

/**
 * 备份文件的预览摘要。恢复前给用户看的「这份备份里有什么」。
 *
 * @param json 原始内容原样带回——确认恢复时直接用，免得二次读文件
 * @param error 非 null = 文件解析失败（损坏/版本过新），habitCount 等字段无意义
 */
data class BackupSummary(
    val habitCount: Int = 0,
    val recordCount: Int = 0,
    val exportedAt: Long = 0L,
    val json: String = "",
    val error: String? = null
)

/**
 * 把「习惯」+「打卡记录」组装成界面要的 HabitUi。
 *
 * 抽成顶层函数而不是 ViewModel 的方法，是为了让它不依赖任何 Android 类，
 * 将来可以脱离模拟器直接写单元测试。
 */
private fun buildHabitUiList(
    habits: List<Habit>,
    records: List<HabitRecord>
): List<HabitUi> {
    val today = todayString()
    val datesByHabit: Map<Long, Set<String>> =
        records.groupBy { it.habitId }
            .mapValues { (_, list) -> list.map { it.date }.toSet() }

    return habits.map { habit ->
        val dates = datesByHabit[habit.id].orEmpty()
        HabitUi(
            id = habit.id,
            name = habit.name,
            emoji = habit.emoji,
            colorArgb = habit.colorArgb,
            streak = calcStreak(dates),
            doneToday = dates.contains(today),
            last7 = last7Days(dates),
            pinned = habit.pinned,
            category = habit.category
        )
    }
}

/**
 * 算出月历网格覆盖的日期范围。
 *
 * 月历是 6 行 × 7 列，第一行通常要补上上个月末尾几天、最后一行补下个月开头几天，
 * 否则「1 号是周三」的时候月份会看起来错位。
 *
 * 周起始按**周一**（中国习惯）。LocalDate.dayOfWeek：周一=1 … 周日=7。
 *
 * @param anchor 当月任意一天（内部统一取 1 号）
 * @return 网格的第一天 到 最后一天
 */
private fun monthGridRange(anchor: LocalDate): Pair<LocalDate, LocalDate> {
    val first = anchor.withDayOfMonth(1)
    // 周起始按周一（中国习惯）。LocalDate.dayOfWeek：周一=1 … 周日=7
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    // 固定 42 天（6 周）：让界面和这里算出**完全一致**的范围。
    // 如果按「本月实际占几周」动态算，28 天的月份只有 4 行，
    // 界面画 6 行时后两行就会查不到数据，出现「下个月初明明有提醒却显示空白」的怪事。
    // 固定 6 行是绝大多数日历 App 的做法，代价只是个别月份多显示几天。
    return gridStart to gridStart.plusDays(41)
}

/**
 * ViewModel 工厂。
 *
 * 为什么需要它？因为 HabitViewModel 的构造函数要带参数（DAO + Context），
 * 而系统默认的创建方式只支持无参构造。工厂告诉系统「该怎么写死这些参数」。
 *
 * 这是不用 Hilt（依赖注入框架）时最朴素的做法。
 * 等哪天页面多了、依赖关系复杂了，再上 Hilt 不迟——现在上纯属给自己加戏。
 */
class HabitViewModelFactory(
    private val habitDao: HabitDao,
    private val recordDao: HabitRecordDao,
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            return HabitViewModel(habitDao, recordDao, appContext) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类型：$modelClass")
    }
}
