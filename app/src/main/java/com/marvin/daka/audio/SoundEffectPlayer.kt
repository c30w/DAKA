package com.marvin.daka.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.marvin.daka.R

/**
 * DAKA 音效播放器。
 *
 * 用 SoundPool 而非 MediaPlayer：UI 短音效需要「低延迟 + 可叠加 + 可预加载」，
 * 这正是 SoundPool 的设计场景（MediaPlayer 适合长音频/音乐）。
 *
 * 设计原则（对应产品要求「清脆抓耳但不突兀、心旷神怡」）：
 * 1. **音量克制**：streamVolume 定在 0.4，不抢系统提示音的风头。
 * 2. **异步预加载**：SoundPool.load 是异步的，在 Application/首次使用时加载，
 *    首次播放前声音已就绪（若没就绪，SoundPool 会静默丢这一次，不崩不响）。
 * 3. **全局开关**：[enabled] 可被设置页控制，关了就完全不发声。
 *
 * 单例：整个进程共享一个 SoundPool（多实例会各占一块音频缓冲，浪费）。
 */
object SoundEffectPlayer {

    private const val TAG = "SoundEffectPlayer"
    private const val MAX_STREAMS = 5
    /** 音量系数：0~1，克制不突兀 */
    private const val VOLUME = 0.4f

    /** 音效枚举 → raw 资源 id */
    enum class Effect(val resId: Int) {
        /** 打卡成功：清脆上扬 "叮" */
        DakaOk(R.raw.daka_ok),
        /** 取消打卡：柔和下行 */
        DakaCancel(R.raw.daka_cancel),
        /** 置顶：明亮升调 */
        DakaPin(R.raw.daka_pin),
        /** 拖拽归位：短促弹跳 */
        DakaDrag(R.raw.daka_drag),
        /** 删除：中低频稍沉 */
        DakaDelete(R.raw.daka_delete),
        /** 打开编辑：干净短促 */
        DakaEdit(R.raw.daka_edit),
        /** 今日全部完成：悦耳双音上行 */
        DakaAllDone(R.raw.daka_all_done),
    }

    @Volatile private var soundPool: SoundPool? = null
    private val loadedIds = mutableMapOf<Effect, Int>()
    private var initialized = false

    /** 全局音效开关。设置页可改，默认开。 */
    @Volatile var enabled: Boolean = true

    /**
     * 初始化并预加载全部音效。进程内只需调用一次。
     * 幂等：重复调用不会重复创建 SoundPool / 重复 load。
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val builder = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(builder)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    // 反向映射：加载完成回调里拿到 sampleId，反查是哪个 Effect
                    Effect.entries.forEach { e ->
                        if (loadedIds[e] == sampleId) {
                            // 已记录，无需额外动作（SoundPool 加载完成后即可播放）
                        }
                    }
                } else {
                    Log.w(TAG, "音效加载失败，status=$status")
                }
            }
            // 预加载全部
            val appCtx = context.applicationContext
            Effect.entries.forEach { e ->
                loadedIds[e] = pool.load(appCtx, e.resId, 1)
            }
            soundPool = pool
            initialized = true
        }
    }

    /**
     * 播放一个音效。开关关闭或尚未初始化时静默忽略。
     */
    fun play(effect: Effect) {
        if (!enabled) return
        val pool = soundPool ?: return
        val sampleId = loadedIds[effect] ?: return
        try {
            pool.play(sampleId, VOLUME, VOLUME, 1, 0, 1.0f)
        } catch (t: Throwable) {
            // SoundPool 播放失败绝不能让 UI 崩，静默吞掉
            Log.w(TAG, "播放失败：$effect", t)
        }
    }

    /** 释放资源（进程结束前由系统回收，一般不需要手动调） */
    fun release() {
        synchronized(this) {
            soundPool?.release()
            soundPool = null
            initialized = false
            loadedIds.clear()
        }
    }
}
