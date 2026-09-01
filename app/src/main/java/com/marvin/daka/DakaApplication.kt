package com.marvin.daka

import android.app.Application
import android.content.Context
import com.marvin.daka.data.LanguagePrefs

/**
 * 全局 Application。
 *
 * 唯一职责：在 **应用级**上下文就套上用户选的语言，
 * 这样任何从 applicationContext 取资源的场景（通知、未来可能的其它进程）都能拿到本地化文案。
 * 真正的界面绘制用的是 Activity 的上下文，语言在 MainActivity.attachBaseContext 里也会再套一次。
 */
class DakaApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguagePrefs.applyLocale(base))
    }
}
