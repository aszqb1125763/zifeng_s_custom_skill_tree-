package org.zifeng.skilltree.client;

import java.lang.reflect.Method;

/**
 * Iris 光影软检测工具（2026-09-11 性能修复）。
 *
 * <h3>为什么需要它</h3>
 * 本模组的世界渲染器需要判断「当前是否处于 Iris 阴影 pass」以便跳过绘制
 * （避免轮廓线在阴影里闪烁）。为避免编译期依赖 Iris jar，用反射调用
 * {@code net.irisshaders.iris.api.v0.IrisApi}。
 *
 * <h3>⚠️ 修复的问题（原实现每帧抛异常）</h3>
 * 原代码把 {@code isIrisShadowPass()} 复制到多个渲染器里，且在
 * {@code RenderLevelStageEvent} 回调（每帧）的<b>最前面</b>调用。未装 Iris 时：
 * <pre>
 *   缓存变量为 null  →  Class.forName(...)  每次都抛 ClassNotFoundException
 *   （异常发生在赋值前 → 变量永远为 null → 没有任何"已失败"缓存）
 * </pre>
 * 结果是每帧都抛异常（含 fillInStackTrace 填充调用栈）。
 *
 * <h3>修复方案</h3>
 * <ul>
 *   <li>统一到本类（消除重复代码）</li>
 *   <li>用 {@code resolveAttempted} 标记记录「<b>已尝试解析</b>」：
 *       即使失败也只 {@code Class.forName} 一次，之后直接短路返回，<b>零异常</b></li>
 *   <li>{@code Method} 对象成功后缓存</li>
 * </ul>
 * 装了 Iris 时行为完全不变；没装 Iris 时从「每帧抛异常」变为「首次解析失败后永久短路」。
 */
public final class IrisCompat {
    private IrisCompat() {
    }

    private static Method getInstance;
    private static Method isShaderPackInUse;
    private static Method isRenderingShadowPass;

    /** 是否已尝试解析（无论成功失败，只解析一次 —— 这是修复的关键） */
    private static volatile boolean resolveAttempted = false;
    /** 解析是否成功（Iris 是否可用） */
    private static volatile boolean available = false;

    /**
     * 当前是否处于 Iris 阴影 pass（无 Iris / 未开光影 → false）。
     * <p>每帧可安全调用：未装 Iris 时首次解析失败后立即短路，不再抛异常。
     */
    public static boolean isShadowPass() {
        if (!resolveAttempted) {
            resolve();
        }
        if (!available) {
            return false; // 无 Iris：零开销短路
        }
        try {
            Object instance = getInstance.invoke(null);
            if (instance == null) {
                return false;
            }
            if (!(Boolean) isShaderPackInUse.invoke(instance)) {
                return false; // 未开光影
            }
            return (Boolean) isRenderingShadowPass.invoke(instance); // 阴影 pass
        } catch (Throwable t) {
            available = false; // 运行期 API 变动 → 永久降级，避免每帧抛异常
            return false;
        }
    }

    /** 解析 Iris API（只执行一次；失败也记为已尝试） */
    private static synchronized void resolve() {
        if (resolveAttempted) {
            return;
        }
        try {
            Class<?> irisApiCls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance = irisApiCls.getMethod("getInstance");
            isShaderPackInUse = irisApiCls.getMethod("isShaderPackInUse");
            isRenderingShadowPass = irisApiCls.getMethod("isRenderingShadowPass");
            available = true;
        } catch (Throwable ignored) {
            available = false; // 无 Iris：正常情况
        }
        resolveAttempted = true;
    }
}
