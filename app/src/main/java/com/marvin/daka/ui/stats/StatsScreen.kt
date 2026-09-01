package com.marvin.daka.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marvin.daka.R
import com.marvin.daka.ui.home.HabitViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * 统计页 —— 把「我到底坚持得怎么样」变成一眼能看懂的图。
 *
 * 三个范围各用一种图，不是炫技，是因为**天数决定了哪种图读得懂**：
 *   - 7 天：柱子。天数少，每天一个独立个体，比高低最直接。
 *   - 30 天：折线。30 根柱子太密，趋势才是重点，线比柱更容易看出「在涨还是在跌」。
 *   - 365 天：热力图。365 个点任何坐标轴都糊成一团，只有「疏密+深浅」能表达
 *     （GitHub 贡献图就是这个思路：不看数值，看「哪片是亮的」）。
 *
 * 所有图都**不引第三方图表库**：三个图合计不到 200 行 Canvas，
 * 而一个图表库会带来几百 KB 体积 + 一套需要学习的 API + 主题适配的坑。
 * 自己画还能精确控制颜色跟着 App 主题走（深色模式自动适配）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: HabitViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val range by viewModel.statsRange.collectAsStateWithLifecycle()
    val daily by viewModel.dailyStats.collectAsStateWithLifecycle()
    val ranks by viewModel.habitStats.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 页面内容加起来比屏幕高（图 + 排行），必须能滚
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ---- 范围切换 ----
            RangeSelector(
                selected = range,
                onSelect = { viewModel.setStatsRange(it) }
            )

            // ---- 概览：三个数字 ----
            OverviewRow(daily = daily)

            // ---- 主图：按范围换形态 ----
            when (range) {
                StatsRange.WEEK -> WeekBarChart(daily = daily)
                StatsRange.MONTH -> MonthLineChart(daily = daily)
                StatsRange.YEAR -> YearHeatmap(daily = daily)
            }

            // ---- 每个习惯的完成率排行 ----
            HabitRankList(ranks = ranks)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------
// 范围切换
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selected: StatsRange,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == selected,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatsRange.entries.size
                ),
                label = {
                    Text(
                        when (item) {
                            StatsRange.WEEK -> stringResource(R.string.stats_range_week)
                            StatsRange.MONTH -> stringResource(R.string.stats_range_month)
                            StatsRange.YEAR -> stringResource(R.string.stats_range_year)
                        }
                    )
                }
            )
        }
    }
}

// ------------------------------------------------------------------
// 概览三数字
// ------------------------------------------------------------------

/**
 * 三块概览：整体完成率 / 全勤天数 / 平均每天完成数。
 *
 * 完成率的分母是**所有天的 activeCount 之和**（而不是习惯数 × 天数），
 * 这样「8 月只有 3 个习惯、9 月有 10 个」也能算出一个真实的总完成率。
 */
@Composable
private fun OverviewRow(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    val totalActive = daily.sumOf { it.activeCount }
    val totalDone = daily.sumOf { it.doneCount }
    val overallRate = if (totalActive == 0) 0f else totalDone.toFloat() / totalActive
    val perfectDays = daily.count { it.isPerfect }
    // 平均只数「有习惯的日子」，否则早期没建习惯的空窗期会把平均值拉低
    val activeDays = daily.count { it.activeCount > 0 }
    val avgPerDay = if (activeDays == 0) 0f else totalDone.toFloat() / activeDays

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OverviewCard(
            modifier = Modifier.weight(1f),
            value = "${(overallRate * 100).toInt()}%",
            label = stringResource(R.string.stats_overall_rate)
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            value = "$perfectDays",
            label = stringResource(R.string.stats_perfect_days)
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            value = "%.1f".format(avgPerDay),
            label = stringResource(R.string.stats_avg_done)
        )
    }
}

@Composable
private fun OverviewCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ------------------------------------------------------------------
// 周：柱状图
// ------------------------------------------------------------------

/**
 * 近 7 天的柱状图。
 *
 * 用 Compose 布局（Row + 权重 + 按比例撑高）而不是 Canvas：
 * 只有 7 根柱子，布局系统完全够用，而且能自动适配宽度、
 * 文字标签也能用普通 Text（Canvas 里画文字要自己算基线，麻烦且容易糊）。
 */
@Composable
private fun WeekBarChart(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    val locale = appLocale()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_week_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                daily.forEach { stat ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 柱子上方的完成数。0 的时候不显示，免得一堆 0 晃眼
                        if (stat.doneCount > 0) {
                            Text(
                                text = "${stat.doneCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        // 柱子本体：按比例撑满可用高度。
                        // 至少留 4dp 的「底」——0% 的日子也要能看到一格淡淡的底
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(stat.rate.coerceIn(0f, 1f))
                                    .background(
                                        color = if (stat.rate > 0f) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        },
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            // 星期窄名（一/二/三…）。跟随应用语言，不是系统语言
                            text = stat.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_chart_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ------------------------------------------------------------------
// 月：折线图
// ------------------------------------------------------------------

/**
 * 近 30 天的完成率折线。
 *
 * 画三层：填充渐变（面积感）→ 折线 → 数据点。
 * 25%/50%/75%/100% 四条虚线网格帮助读数——没有网格的折线图只能看形状，看不出具体高低。
 */
@Composable
private fun MonthLineChart(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val fillTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val fillBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_month_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                if (daily.isEmpty()) return@Canvas
                val w = size.width
                val h = size.height
                // 上下留 8px 边距，让 100% 的点不被裁掉顶部
                val top = 8f
                val usable = h - top - 8f
                val stepX = if (daily.size > 1) w / (daily.size - 1) else 0f

                fun xOf(i: Int) = i * stepX
                fun yOf(rate: Float) = top + usable * (1f - rate.coerceIn(0f, 1f))

                // 1) 水平网格：25/50/75/100 四条虚线
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                    val y = yOf(level)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }

                // 2) 面积填充：从折线到画布底部，营造「累积」的感觉
                val fillPath = Path().apply {
                    moveTo(xOf(0), h)
                    daily.forEachIndexed { i, stat -> lineTo(xOf(i), yOf(stat.rate)) }
                    lineTo(xOf(daily.lastIndex), h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillTop, fillBottom),
                        startY = top,
                        endY = h
                    )
                )

                // 3) 折线本体
                val linePath = Path().apply {
                    moveTo(xOf(0), yOf(daily.first().rate))
                    daily.forEachIndexed { i, stat -> if (i > 0) lineTo(xOf(i), yOf(stat.rate)) }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 6f)
                )

                // 4) 数据点：只在「有打卡」的日子画，空白日子不画点，
                //    否则一条 0% 的折线会挂满底部的小圆点，很脏
                daily.forEachIndexed { i, stat ->
                    if (stat.rate > 0f) {
                        drawCircle(
                            color = lineColor,
                            radius = 7f,
                            center = Offset(xOf(i), yOf(stat.rate))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = daily.firstOrNull()?.date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.stats_chart_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(R.string.stats_today_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 年：热力图
// ------------------------------------------------------------------

private val CELL: Dp = 13.dp
private val CELL_GAP: Dp = 3.dp
private val WEEKDAY_LABEL_W: Dp = 18.dp

/**
 * 近一年的日历热力图（GitHub 贡献图那种）。
 *
 * 布局：一列 = 一周，一行 = 星期几（第一行周一）。
 * 一年 53 列 × 15dp ≈ 795dp，手机屏幕放不下，所以整体横向滚动，
 * 并且**进入时自动滚到最右**（最新的一周在右下角，符合阅读直觉）。
 *
 * 颜色分五档而不是连续渐变：人眼区分不了 365 种深浅，
 * 五档足够表达「没做/偶尔/一半/经常/全勤」，还能避免相邻格子颜色糊在一起。
 */
@Composable
private fun YearHeatmap(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val locale = appLocale()
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val monthLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 首次进入滚到最右：让用户第一眼看到的是「最近」，而不是一年前
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_year_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (daily.isEmpty()) return@Column

            val first = daily.first().date
            // 首日是周几（周一=0），决定第一列要空几格
            val leading = first.dayOfWeek.value - 1
            val columns = ceil((leading + daily.size) / 7.0).toInt()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                // 左侧星期标签：只标周一/周三/周五，全标会挤成一团
                Column(
                    modifier = Modifier.width(WEEKDAY_LABEL_W),
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP)
                ) {
                    repeat(7) { row ->
                        Box(modifier = Modifier.size(CELL)) {
                            if (row == 0 || row == 2 || row == 4) {
                                Text(
                                    text = LocalDate.now()
                                        .with(java.time.temporal.WeekFields.ISO.dayOfWeek(), row + 1L)
                                        .dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = monthLabelColor,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))

                Canvas(
                    modifier = Modifier
                        .width((CELL + CELL_GAP) * columns)
                        .height((CELL + CELL_GAP) * 7)
                ) {
                    val cellPx = CELL.toPx()
                    val gapPx = CELL_GAP.toPx()
                    val step = cellPx + gapPx

                    daily.forEachIndexed { i, stat ->
                        val slot = leading + i
                        val col = slot / 7
                        val row = slot % 7
                        val left = col * step
                        val top = row * step

                        val color = when {
                            stat.rate <= 0f -> emptyColor
                            // 五档：按完成率递增不透明度
                            stat.rate < 0.25f -> primary.copy(alpha = 0.25f)
                            stat.rate < 0.5f -> primary.copy(alpha = 0.45f)
                            stat.rate < 0.75f -> primary.copy(alpha = 0.65f)
                            stat.rate < 1f -> primary.copy(alpha = 0.85f)
                            else -> primary
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(cellPx, cellPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // 图例：从左（没做）到右（全勤）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.stats_legend_low),
                    style = MaterialTheme.typography.labelSmall,
                    color = monthLabelColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(0f, 0.3f, 0.6f, 0.85f, 1f).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(CELL)
                            .background(
                                color = if (level == 0f) emptyColor else primary.copy(alpha = level),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.stats_legend_high),
                    style = MaterialTheme.typography.labelSmall,
                    color = monthLabelColor
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 习惯完成率排行
// ------------------------------------------------------------------

@Composable
private fun HabitRankList(
    ranks: List<HabitStat>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_rank_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (ranks.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            ranks.forEach { stat ->
                HabitRankRow(stat = stat)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HabitRankRow(
    stat: HabitStat,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stat.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stat.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(stat.rate * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 进度条：习惯自己的主题色，跟首页卡片保持一致，一眼能对上
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.rate.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = Color(stat.colorArgb),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.stats_rank_days, stat.doneDays, stat.activeDays),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 应用当前语言。
 *
 * ⚠️ 不能用 Locale.getDefault()：App 内切了语言后，JVM 默认 locale
 * 不会跟着变（attachBaseContext 改的是 Configuration，不是 JVM），
 * 星期名会跟着系统语言走，出现「界面英文 + 星期中文」的混搭。
 */
@Composable
private fun appLocale(): Locale {
    val context = LocalContext.current
    return remember(context) {
        context.resources.configuration.locales[0] ?: Locale.getDefault()
    }
}
