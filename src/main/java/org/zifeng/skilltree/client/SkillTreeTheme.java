package org.zifeng.skilltree.client;

/**
 * 技能树界面主题（★ 2026-10-09 新增黑暗模式，双版本同构）。
 *
 * <h2>为什么要有这个类</h2>
 * 技能树界面的颜色此前<b>全部硬编码</b>（{@code SkillTreeScreen} 里 240+ 处字面量），
 * 且存在一个结构性问题：<b>贴片（大/子底）是浅色，而技能贴片是深色</b> ——
 * 浅色大底铺满全屏，这就是玩家反馈「界面太亮」的来源。
 * 故把「<b>结构面色</b>」（底/描边/底上的文字）集中到本类，
 * 由 {@link #isDark()} 一次性切换；其余<b>语义色</b>（类别强调色、状态色、技能名/消耗色）
 * <b>一律不动</b> —— 它们本来就是深底上的浅字，且是辨识度的来源。
 *
 * <h2>设计取舍（用户原话：不能太黑、不能一棍子打死、要有辨识度）</h2>
 * <ul>
 *   <li><b>不用纯黑</b>：底用 {@code #191920} / {@code #22222B} 这级「深灰蓝」，
 *       而不是 {@code #000000}，避免对比过硬、长时间看眼睛累。</li>
 *   <li><b>保留层级</b>：大底 → 子底 → 类别按钮，三级仍有明度差（越靠内越亮），
 *       与浅色模式同构，只是整体压暗。</li>
 *   <li><b>贴片提亮</b>：技能贴片原本就是深色，若直接放到深色底上会「融在一起」，
 *       故黑暗模式下用 {@link #tileBg(int)} 把贴片底色<b>朝白提亮 10%</b>，
 *       让贴片从底上「浮」起来；贴片的类别色描边与左侧色条保持不变 → 辨识度不受影响。</li>
 * </ul>
 *
 * <p>⚠️ 全部色值集中在本类，玩家/作者想微调黑暗程度只需改这一处。
 */
public final class SkillTreeTheme {
    private SkillTreeTheme() {
    }

    /** 是否处于黑暗模式（读客户端本地设置，存 config/zifeng_s_custom_skill_tree_client.json） */
    public static boolean isDark() {
        return SkillKeyBinds.isSkillTreeDarkMode();
    }

    // ==================== 结构面（贴片底与描边）====================

    /** 底层大贴片底色（铺满整屏）—— 亮度的主要来源 */
    public static int bigBg() {
        return isDark() ? 0xFF191920 : 0xFFF4F5F8;
    }

    /** 子贴片（标题/类别/技能行三块）底色 */
    public static int subBg() {
        return isDark() ? 0xFF22222B : 0xFFE8EAF0;
    }

    /** 子贴片 1px 描边 */
    public static int subEdge() {
        return isDark() ? 0xFF3A3A46 : 0xFFC9CCD5;
    }

    // ==================== 类别按钮（胶囊）====================

    public static int catBg() {
        return isDark() ? 0xFF2C2C37 : 0xFFF6F6FA;
    }

    public static int catBgHover() {
        return isDark() ? 0xFF3B3B49 : 0xFFFFFFFF;
    }

    public static int catText() {
        return isDark() ? 0xFFD6D6E2 : 0xFF4A4A56;
    }

    public static int catTextHover() {
        return isDark() ? 0xFFF2F2FA : 0xFF2A2A34;
    }

    // ==================== 搜索框 ====================

    public static int searchBg() {
        return isDark() ? 0xFF262631 : 0xFFE8EAF0;
    }

    public static int searchBgHover() {
        return isDark() ? 0xFF2F2F3C : 0xFFF6F6FA;
    }

    public static int searchBgFocused() {
        return isDark() ? 0xFF353543 : 0xFFFFFFFF;
    }

    /** 搜索框正文色（浅色模式为深字，黑暗模式为浅字） */
    public static int searchValueText() {
        return isDark() ? 0xFFE8E8F0 : 0xFF2A2A34;
    }

    /** 搜索框占位文案色 */
    public static int searchPlaceholderText() {
        return isDark() ? 0xFF8E8E9C : 0xFF9A9AA6;
    }

    // ==================== 直接画在贴片上的文字/符号 ====================

    /** 贴片上的一般性灰字（如搜索结果计数） */
    public static int onFrameText() {
        return isDark() ? 0xFFC6C6D2 : 0xFF66666E;
    }

    /** 贴片上的弱化灰字/图标（如清空按钮 ✕） */
    public static int onFrameTextDim() {
        return isDark() ? 0xFF9E9EAC : 0xFF8A8A96;
    }

    // ==================== 技能贴片底色 ====================

    /**
     * 技能贴片底色随主题微调。
     * <p>贴片底色本就是深色（{@code #183D35} 等类别色），在浅色大底上对比强烈；
     * 黑暗模式下大底也变深，若不处理会「贴片与底融在一起」→ 故朝白<b>提亮 10%</b>，
     * 让贴片浮起来。类别色相保持不变 → 一眼分辨类别的能力不受影响。
     */
    public static int tileBg(int bg) {
        return isDark() ? lighten(bg, 0.10F) : bg;
    }

    /** 朝白插值（{@code f}=0 原色，{@code f}=1 纯白）；保留 alpha */
    private static int lighten(int argb, float f) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final int nr = Math.min(255, (int) (r + (255 - r) * f));
        final int ng = Math.min(255, (int) (g + (255 - g) * f));
        final int nb = Math.min(255, (int) (b + (255 - b) * f));
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }
}
