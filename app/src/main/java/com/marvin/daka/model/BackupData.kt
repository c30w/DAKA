package com.marvin.daka.model

import kotlinx.serialization.Serializable

/** 当前备份文件格式的版本号。将来改了字段结构就 +1，导入时据此判断能不能读。 */
const val BACKUP_VERSION = 1

/**
 * 一份完整的备份内容。
 *
 * 为什么自己套一层，而不是直接导出两个数组？
 * 因为将来换手机、换 App 版本，备份文件可能来自「老版本的结构」。
 * 有了 [version]，导入时就能判断「这个格式我认不认得」，
 * 而不是拿到一堆 JSON 硬解析，解析到一半才发现字段对不上。
 *
 * 这也是**先定版本号再写功能**的原因：等真出了 v2 格式，v1 的文件已经散落在用户手里了，
 * 那时候再加版本号，老文件就永远没法正确识别。
 *
 * @property version 备份格式版本
 * @property exportedAt 导出时间戳，给用户看「这份备份是什么时候的」
 * @property habits 全部习惯（含已归档的）
 * @property records 全部打卡记录
 */
@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val habits: List<Habit> = emptyList(),
    val records: List<HabitRecord> = emptyList()
)
