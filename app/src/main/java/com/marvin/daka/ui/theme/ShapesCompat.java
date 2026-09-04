package com.marvin.daka.ui.theme;

import androidx.compose.foundation.shape.CornerBasedShape;
import androidx.compose.material3.Shapes;

/**
 * Material3 1.4.0 把 Shapes 的 Kotlin 构造/复制全部标成了 internal / Deprecated(HIDDEN)，
 * Kotlin 侧既不能 new 也不能 copy。但这些成员在 JVM 字节码层面仍是 public，
 * 所以留一个 Java 桥：Java 只认 JVM 可见性，Kotlin 主题层借它生成自定义 Shapes。
 *
 * @param extraSmall 最小档（chip 之类）
 * @param small      小档
 * @param medium     中档（卡片、按钮默认）
 * @param large      大档
 * @param extraLarge 大档（底部弹层、大卡片）
 */
public final class ShapesCompat {
    private ShapesCompat() {
    }

    public static Shapes build(
            CornerBasedShape extraSmall,
            CornerBasedShape small,
            CornerBasedShape medium,
            CornerBasedShape large,
            CornerBasedShape extraLarge
    ) {
        return new Shapes(extraSmall, small, medium, large, extraLarge);
    }
}
