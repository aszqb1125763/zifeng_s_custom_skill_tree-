package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 技能树搜索框的真实输入控件 —— 原版 {@link EditBox} 的薄封装。
 *
 * <h2>一、为什么必须换掉原来的「纯自绘」输入（根因）</h2>
 * 搜索框原本是纯自绘的：文字、光标、退格全由 {@link SkillTreeScreen} 手工处理，
 * 从未调用过 {@code Screen.setFocused(...)}，因此 {@code screen.getFocused()} 永远是 {@code null}。
 *
 * <p>装了输入法类模组（典型为 IMBlocker「输入法冲突修复」）时，这类模组靠
 * 「当前是否存在<b>获得焦点</b>的文本控件」判断要不要放行输入法。
 * {@code getFocused() == null} 会被判定为「非文本输入场景」，
 * 于是它强制把输入法压成英文并锁住切换 —— 表现就是「系统输入法永远识别为英文、切不了中文」。
 *
 * <p>改用原版 {@link EditBox} 承载输入后，{@code getFocused()} 会返回本控件，
 * 输入法模组即可正确放行。
 * <p>顺带白得原版的一整套编辑能力（这些在纯自绘方案里全都没有）：
 * 光标左右移动、Shift 选区、Ctrl+A 全选、Ctrl+C/V/X 复制粘贴剪切、
 * 双击选词、Home/End 跳首尾、输入超长自动横向滚动。
 *
 * <h2>二、来源与致谢（学自其它模组）</h2>
 * <pre>
 *   模组: Applied Energistics 2 (AE2) —— 本模组自带 Ae2Compat 兼容层，同一生态
 *   类:   appeng.client.gui.widgets.AETextField
 *   仓库: https://github.com/AppliedEnergistics/Applied-Energistics-2
 *   路径: src/client/java/appeng/client/gui/widgets/AETextField.java
 *   协议: GNU Lesser General Public License v3.0
 *         Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 * </pre>
 *
 * <p><b>借鉴的是思路，代码为本项目独立编写，未复制 AE2 源码。</b>
 * 具体借鉴了以下三点：
 * <ol>
 *   <li><b>继承原版 {@code EditBox}</b> 而不是继续自绘输入 ——
 *       AE2 的搜索框同样是 {@code class AETextField extends EditBox}，
 *       这是本项目决定改用此方案的直接依据。</li>
 *   <li><b>自己绘制占位文案</b>，不用 {@code setHint} ——
 *       EditBox 渲染 hint 时使用的是 {@code textColor}，做不到「占位灰、正文黑」的对比；
 *       AE2 也选择自绘 placeholder（其注释称之为 placeholder）。</li>
 *   <li><b>把鼠标命中区扩大到整个可视框</b> ——
 *       AE2 的原注释指出这是为了避免
 *       {@code "mouse deadzone that is still visually within the background"}，
 *       即「看起来在框内、点下去却没反应」。</li>
 * </ol>
 *
 * <h2>三、双版本一致性</h2>
 * 本类用到的 API（{@code setBordered} / {@code setTextColor} / {@code setMaxLength} /
 * {@code renderWidget} / {@code isMouseOver} / {@code getX} / {@code getY}）
 * 在 1.20.1 Forge 与 1.21.1 NeoForge 上签名完全一致，
 * 因此本文件在两版本中<b>应保持逐字相同</b>（不要单边改）。
 *
 * <h2>四、与 AE2 实现的差异（有意为之）</h2>
 * <ol>
 *   <li>AE2 用一张 {@code guis/text_field.png} 三态贴图当背景；我们沿用技能树原有的
 *       「圆角 + 描边」自绘外观（{@code fillRound}/{@code strokeRound}），
 *       所以这里 {@code setBordered(false)}，只负责画文字、光标与选区高亮。</li>
 *   <li>AE2 还重写 {@code mouseClicked} 把坐标 clamp 进 EditBox 范围；
 *       我们的左内边距只有 {@link #PAD_X} 像素，且 {@code EditBox.onClick} 对
 *       越界坐标本就会把光标夹到首/尾，因此省去该步骤（少一层改写少一处风险）。</li>
 *   <li><b>不转发 {@code tick()}</b>（有意省略，不是遗漏）——
 *       {@code EditBox} 的光标闪烁在 1.20.1 由内部计数器 {@code frame} 驱动
 *       （仅 {@code EditBox.tick()} 自增），而 1.21.1 的 {@code AbstractWidget}
 *       已<b>移除</b> {@code tick()}，两版本没有公共的 tick 入口。
 *       写版本分支只为换一个光标闪烁并不值（用户 2026-09-20 确认不需要闪烁）。
 *       影响：1.20.1 上光标保持<b>常亮</b>而不闪，可见性不受影响。</li>
 * </ol>
 */
public class SkillSearchBox extends EditBox {

    /** 内层输入区相对可视框的左内边距（与原自绘方案的文字起始位置对齐） */
    public static final int PAD_X = 1;
    /** 内层输入区相对可视框的上内边距（让文字在框内垂直居中） */
    public static final int PAD_Y = 3;

    /**
     * 原版 {@code EditBox} 只暴露了 {@code setHighlightPos(int)}，<b>没有 getter</b>，
     * 而选区高亮需要知道选区的另一端（锚点）在哪。这里用反射读私有字段
     * {@code highlightPos}。
     *
     * <p>1.21.1 NeoForge 运行时用官方映射（无 reobf），字段名就是 {@code highlightPos}；
     * {@code f_94102_} 是 1.20.1 Forge reobf 后的 SRG 名，为保持两版本可共用同一份代码而一并尝试。
     * <p>读取失败时退化为返回光标位置 —— 即不显示选区，但绝不会抛异常崩界面。</p>
     */
    private static final java.lang.reflect.Field HIGHLIGHT_POS_FIELD = findHighlightPosField();

    private static java.lang.reflect.Field findHighlightPosField() {
        for (final String name : new String[]{"highlightPos", "f_94102_"}) {
            try {
                final java.lang.reflect.Field field =
                        net.minecraft.client.gui.components.EditBox.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (final Throwable ignored) {
                // 换下一个候选名
            }
        }
        return null;
    }

    /** 读选区锚点位置；失败时退化为光标位置（等价于"没有选区"）。 */
    private int highlightPos() {
        if (HIGHLIGHT_POS_FIELD == null) {
            return getCursorPosition();
        }
        try {
            return HIGHLIGHT_POS_FIELD.getInt(this);
        } catch (final Throwable ignored) {
            return getCursorPosition();
        }
    }

    /** 输入长度上限。原版默认 32，对「技能 ID + 中文名」的搜索偏短，放宽到 64 */
    private static final int MAX_LENGTH = 64;

    /** 绘制用字体。★ 不再是 final —— 见 {@link #setTextFont(Font)} 的说明。 */
    private Font textFont;

    /**
     * 更新字体引用（★ 2026-09-21 修复「输入英文时字母糊在一起」）。
     *
     * <p><b>根因</b>：{@code Font} 实例在资源重载（或字体类模组异步初始化 SDF 字体）后
     * 会被<b>整体替换</b>。{@code SkillTreeScreen.font} 是 {@code Screen} 每次
     * {@code init()} 注入的最新实例，而本字段原先在构造时<b>一次性捕获且为 final</b> ——
     * 于是搜索框一直用旧 Font：旧的字形图集/字距表在非整数缩放或放大后发糊，
     * 表现为<b>字母挤在一起看不清</b>，而界面其余文字（用最新 font）正常。
     *
     * <p>现在由 {@code SkillTreeScreen.renderSearchBox()} 每帧同步最新引用。
     * <p>⚠️ 原版 {@code EditBox.font} 是 {@code private final} 且<b>没有 setter</b>，
     * 无法原地更新；因此 {@code ensureSearchBox()} 还会对比 {@link #getTextFont()}
     * 判断是否需要<b>重建控件</b>（重建时会保留输入内容与焦点）。
     */
    public void setTextFont(Font font) {
        if (font != null && font != this.textFont) {
            this.textFont = font;
            this.scrollPos = 0; // 字距变了 → 重置横向滚动起点
        }
    }

    /** 当前持有的字体。供外部判断是否需要重建控件（原版 {@code EditBox.font} 是 final，无法更新）。 */
    public Font getTextFont() {
        return textFont;
    }

    /** 整个可视圆角框的边界（含内边距与 ✕ 按钮占位），用于命中判定 */
    private final int visualLeft;
    private final int visualTop;
    private final int visualRight;
    private final int visualBottom;
    /** 占位文案（无输入且未聚焦时显示）；语言重载后由 {@link SkillTreeScreen} 更新 */
    private String placeholder;
    private final int placeholderColor;
    /** 正文颜色；自己保存，避免反射读原版 {@code EditBox.textColor} */
    private final int valueColor;
    /** 横向滚动起点（字符下标）。原版字段是 private，这里自己维护，保证长文本滚动不错位。 */
    private int scrollPos;

    /**
     * @param left           可视圆角框左边界
     * @param top            可视圆角框上边界
     * @param width          可视圆角框宽度
     * @param height         可视圆角框高度
     * @param rightReserve   右端需要让出的宽度（✕ 清空按钮占位 + 与文字的最小间距），
     *                       避免长关键词画到 ✕ 底下
     * @param placeholder    占位文案（未输入且未聚焦时显示），由语言文件提供
     * @param textColor      正文颜色（ARGB）
     * @param placeholderColor 占位文案颜色（ARGB）
     */
    public SkillSearchBox(Font font, int left, int top, int width, int height,
                          int rightReserve, String placeholder,
                          int textColor, int placeholderColor) {
        // ★ 学自 AE2（借鉴点 ①）：内层输入区往右下偏移一个内边距，
        //   外层留出的空间正好用来画自己的背景与描边。
        super(font, left + PAD_X, top + PAD_Y,
                Math.max(1, width - PAD_X - rightReserve),
                Math.max(1, height - PAD_Y * 2), Component.empty());
        this.textFont = font;
        this.visualLeft = left;
        this.visualTop = top;
        this.visualRight = left + width;
        this.visualBottom = top + height;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.placeholderColor = placeholderColor;
        this.valueColor = textColor;
        this.scrollPos = 0;

        // 背景与描边由 SkillTreeScreen 用圆角自绘（保持既有外观），这里只管文字
        setBordered(false);
        setMaxLength(MAX_LENGTH);
        setTextColor(textColor);
    }

    /** 更新占位文案（语言切换后需要重取翻译） */
    public void setPlaceholder(String text) {
        this.placeholder = text == null ? "" : text;
    }

    @Override
    public void setValue(String text) {
        super.setValue(text);
        ensureScrollVisible();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) {
            ensureScrollVisible();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) {
            ensureScrollVisible();
        }
        return handled;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        ensureScrollVisible();
    }

    /**
     * 命中区扩大到整个可视框 —— 学自 AE2（借鉴点 ③）。
     *
     * <p>若不重写，命中范围只有内层输入区：顶部/底部各 {@link #PAD_Y} 像素与
     * 右侧 ✕ 之前的留白就都成了「视觉在框内、点击无反应」的死区。
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= visualLeft && mouseX < visualRight
                && mouseY >= visualTop && mouseY < visualBottom;
    }

    /**
     * 绘制输入内容（文字 + 光标 + 选区高亮），并在空且未聚焦时补画占位文案。
     *
     * <p>★ 学自 AE2（借鉴点 ②）：占位文案自己画，不用 {@code setHint} ——
     * EditBox 渲染 hint 用的是 {@code textColor}，无法做到占位灰、正文黑。
     */
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderCrispContents(guiGraphics);
    }

    /**
     * 自己画输入内容，避开技能树外层 {@code pose.scale(r)}。
     *
     * <p>原版 {@link EditBox} 的文字、光标、选区都走当前 pose。技能树为了独立于
     * GUI Scale，会给整棵界面套一层非整数缩放；位图字形经过这层缩放后边缘会发糊，
     * 英文搜索内容也会一起变糊。这里把当前 pose 的平移保留下来、把缩放抵消掉，
     * 让字形按原版整数 GUI Scale 绘制。字号因此保持原版清晰度，不随窗口或自定义缩放变化。
     *
     * <p>★ 2026-09-21：所有文字/光标一律<b>不带投影</b>（{@code dropShadow = false}）。
     * 本搜索框是近白底，而原版投影是右下 (+1,+1) 的深色硬边字 —— 压在浅底上会变成
     * 清清楚楚的「第二层字」，看起来就像重影。
     */
    private void renderCrispContents(GuiGraphics guiGraphics) {
        final String value = getValue();
        final boolean showPlaceholder = !placeholder.isEmpty() && value.isEmpty() && !isFocused();
        final int textColor = showPlaceholder ? placeholderColor : valueColor;
        final String shown = showPlaceholder ? placeholder : value;

        final var pose = guiGraphics.pose();
        final org.joml.Matrix4f m = pose.last().pose();
        final float sx = m.m00();
        final float sy = m.m11();
        final float tx = m.m30();
        final float ty = m.m31();
        final boolean scaled = Math.abs(sx - 1.0F) > 0.001F || Math.abs(sy - 1.0F) > 0.001F;

        pose.pushPose();
        if (scaled) {
            pose.setIdentity();
            // EditBox 位于技能树的非整数缩放层内。即使取消了字号缩放，
            // 浮点平移仍会让位图字形落在半像素边界，产生截图中“重影”效果。
            // 将最终屏幕坐标吸附到整数像素，保持文字和光标完全对齐。
            pose.translate(Math.round(tx + getX() * sx), Math.round(ty + getY() * sy), 0.0F);
        }

        final int localX = scaled ? 0 : getX();
        final int localY = scaled ? 0 : getY();
        final int boxW = getWidth();
        ensureScrollVisible();
        final int displayPos = Math.max(0, Math.min(scrollPos, shown.length()));
        final String visible = textFont.plainSubstrByWidth(shown.substring(Math.min(displayPos, shown.length())), boxW);
        final int cursor = getCursorPosition();
        final int highlight = highlightPos();
        final int visCursor = cursor - displayPos;
        final int visHighlight = Math.min(highlight - displayPos, visible.length());

        if (!visible.isEmpty()) {
            // ★ 2026-09-21：这里刻意【不带投影】（drawString 的 dropShadow = false）。
            //   原版 EditBox 用 dropShadow = true，因为原版输入框是【深色底】。
            //   而本搜索框是近白底（0xFFE8EAF0 / 悬停 0xFFF6F6FA / 聚焦纯白）；
            //   深色投影画在白底上不再是被背景吸收的“阴影”，而是右下 (+1,+1)
            //   方向、硬边的【第二层字】——看起来就是一个词被叠了两份。
            //   位图字形在 1.0 整数像素下是硬边的，所以这层重影格外清楚。
            guiGraphics.drawString(textFont, visible, localX, localY, textColor, false);
        }

        if (isFocused() && visCursor >= 0 && visCursor <= visible.length() && !showPlaceholder) {
            final String beforeCursor = visible.substring(0, Math.min(visCursor, visible.length()));
            final int cursorX = localX + textFont.width(beforeCursor);
            if (cursor >= shown.length()) {
                // 光标同理不带投影，否则末端会出现一个多余的“影子”竖线
                guiGraphics.drawString(textFont, "_", cursorX, localY, textColor, false);
            } else {
                guiGraphics.fill(cursorX, localY - 1, cursorX + 1, localY + 1 + textFont.lineHeight, 0xFFD0D0D0);
            }
        }

        if (isFocused() && visHighlight != visCursor && visHighlight >= 0) {
            final int selStart = Math.min(visCursor, visHighlight);
            final int selEnd = Math.max(visCursor, visHighlight);
            final int selX0 = localX + textFont.width(visible.substring(0, Math.max(0, Math.min(selStart, visible.length()))));
            final int selX1 = localX + textFont.width(visible.substring(0, Math.max(0, Math.min(selEnd, visible.length()))));
            if (selX1 > selX0) {
                guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),
                        selX0, localY - 1, selX1, localY + 1 + textFont.lineHeight, 0xFF0000FF);
            }
        }

        pose.popPose();
    }

    /** 让光标始终落在可视宽度内，长关键词向右输入时自动横向滚动。 */
    private void ensureScrollVisible() {
        final String value = getValue();
        final int cursor = Math.max(0, Math.min(getCursorPosition(), value.length()));
        final int boxW = Math.max(1, getWidth());
        if (scrollPos > cursor) {
            scrollPos = cursor;
        }
        while (scrollPos < cursor && textFont.width(value.substring(scrollPos, cursor)) > boxW) {
            scrollPos++;
        }
        while (scrollPos > 0 && textFont.width(value.substring(scrollPos - 1, cursor)) <= boxW) {
            scrollPos--;
        }
    }
}
