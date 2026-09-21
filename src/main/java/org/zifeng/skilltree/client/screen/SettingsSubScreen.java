package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * 设置子界面（★ 2026-09-21 新增，由右下角第三个按钮「设置」打开）。
 *
 * <h2>本期范围</h2>
 * 暂时<b>只有一个功能</b>：技能树界面尺寸（用户 2026-09-21 指定）。
 * 后续会在此面板内继续扩展其它设置项。
 *
 * <h2>控件（用户指定样式）</h2>
 * <pre>
 *   [ 界面尺寸 ]  [────●──────]  [−] [ 100 ] [+]
 *       标签         可拖动进度条    箭头   可直接输入数字
 * </pre>
 * <ul>
 *   <li><b>拖动进度条</b>：胶囊样式，与技能行「等级进度条」同一套观感（{@code fillRounded}）</li>
 *   <li><b>数字</b>：左右箭头 −/+（普通=1、Shift=10、Ctrl=100），点击数字本体可直接键入</li>
 * </ul>
 *
 * <h2>取值</h2>
 * 百分比 {@code 50 ~ 200}，<b>基准 100</b>（= 显示器 1920×1080 + 游戏 GUI Scale 3 的观感）。
 * 值持久化在客户端本地配置（{@code SkillKeyBinds}，不跟存档）。
 *
 * <h2>⚠️ 后续未完成的接线（用户后续会讲）</h2>
 * 本期<b>只做界面</b>：拖动/输入会改设置值，但<b>尚未真正缩放技能树</b>。
 * 真正的缩放逻辑（基准坐标系、{@code r = 3z/S} 整体缩放、鼠标坐标反算、
 * 收缩阶梯「留白 → 属性区 → 进度条」）待用户确认后另行接入。
 */
public class SettingsSubScreen extends SkillSubScreen {

    // ============ 面板几何 ============
    /**
     * 面板宽（★ 2026-09-21 暂时写死）。
     *
     * <p>⚠️ 用户已定「子界面宽度改为内容自适应（留白最大 20、可缩到 1）」，
     * 但那是一项涉及<b>三个面板</b>的暂时性改动，尚未实施；本期本面板沿用固定宽，
     * 与 HUD调整/属性面板保持一致，等自适应方案落地时一并改。
     */
    private static final int PANEL_W = 258;
    /** 面板高：标题栏 + 行高 + 底部提示 + 内边距 */
    private static final int PANEL_H = 96;
    /** 行高 */
    private static final int ROW_H = 18;
    /** 内容距面板左右的内边距 */
    private static final int PAD = 12;
    /** 首行顶部（标题栏下方） */
    private static final int ROWS_TOP = TITLE_BAR_H + 10;
    private static final int ROW_GAP = 6;

    // ---- 行内子元素（局部坐标，基准 = panelX）----
    /** 标签宽 */
    private static final int LABEL_W = 56;
    /** 进度条起点 = 内边距 + 标签宽 + 间距 */
    private static final int SLIDER_X = PAD + LABEL_W + 6;
    /** 进度条宽 */
    private static final int SLIDER_W = 88;
    /** 进度条高（胶囊） */
    private static final int SLIDER_H = 8;
    /** 箭头格边长 */
    private static final int ARROW_W = 14;
    /** − 按钮起点（进度条右侧留 8px） */
    private static final int MINUS_X = SLIDER_X + SLIDER_W + 8;
    /** 数字输入框起点 */
    private static final int NUM_X = MINUS_X + ARROW_W + 3;
    /** 数字输入框宽（★ 2026-09-21：收窄到接近文字宽，让左对齐的 EditBox 文字看起来居中） */
    private static final int NUM_W = 34;
    /** + 按钮起点 */
    private static final int PLUS_X = NUM_X + NUM_W + 3;
    /** 数字输入框高 */
    private static final int NUM_H = 14;

    // ---- 取值范围（★ 2026-09-21 用户修正：<b>0 = 基准</b>，+ 加大 / − 缩小）----
    /**
     * 界面上显示的是【相对基准的偏移量】而非百分比：
     * <pre>
     *   0   = 基准（= 显示器 1920×1080 + 游戏 GUI Scale 3 的观感）
     *   +1  = 加大 1%（正数可到 +100 = 200%）
     *   -1  = 缩小 1%（负数可到 -50 = 50%）
     * </pre>
     * <p>⚠️ 早先误实现为「100 = 基准、区间 50~200」，与用户设定的「0 为基准」不符，已改。
     */
    private static final int MIN_OFF = -50;
    private static final int MAX_OFF = 100;
    /** 显示为百分数的换算基准（0 偏移 = 100%） */
    private static final int BASE_PCT = 100;

    // ---- 配色（沿用子界面既有色系）----
    private static final int C_LABEL = 0xFFE0B6C8;
    private static final int C_TRACK = 0x59FFFFFF;
    private static final int C_FILL = 0xFF66CCFF;
    private static final int C_HANDLE = 0xFFFFDD44;
    private static final int C_ARROW = 0xFF87CEEB;
    private static final int C_NUM_BG = 0xFF2A2A3A;
    private static final int C_NUM_EDGE = 0xFF555566;
    private static final int C_NUM_TEXT = 0xFFFFE9A0;
    private static final int C_HINT = 0xFF888888;

    /** 数值输入框（原版 EditBox，只收数字；点击数字本体时获得焦点） */
    private EditBox numBox = null;
    /** 正在拖动进度条 */
    private boolean draggingSlider = false;

    public SettingsSubScreen(SkillTreeScreen parent) {
        super(parent);
        this.posKey = "settings";
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        panelW = PANEL_W;
        panelH = PANEL_H;
        // 默认居中（与 HUD调整一致）
        panelX = (screenWidth - panelW) / 2;
        panelY = (screenHeight - panelH) / 2;
        super.init(screenWidth, screenHeight); // 恢复上次拖动位置（有保存值则覆盖）
        ensureNumBox();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        renderPanelBase(gui, t("settings_title"), mouseX, mouseY);
        renderModeRow(gui, mouseX, mouseY);
        renderGuiScaleRow(gui, mouseX, mouseY);
        // 底部提示（★ 2026-09-21：去掉投影）
        gui.drawString(parent.font(), t("settings_gui_hint"),
                panelX + PAD, panelY + panelH - 11, C_HINT, false);
        // 面板可能刚被拖动过 → 同步输入框位置（否则会与自绘底色脱节）
        repositionNumBox();
        // 数字输入框（最后画，压在自绘底色之上）
        if (numBox != null) {
            numBox.render(gui, mouseX, mouseY, 0f);
        }
    }

    /**
     * 同步输入框位置（★ 拖动面板时必须调）。
     *
     * <p>{@code EditBox} 的位置是创建时写定的，而面板可以被标题栏拖动（{@code panelX/panelY} 会变），
     * 不重新同步的话输入框会“留在原地”与自绘底色脱节。
     * <p>只改位置（不重建）→ 保住光标与选区，不会把玩家正在输入的焦点弄丢。
     */
    private void repositionNumBox() {
        if (numBox == null) {
            return;
        }
        int[] g = numBoxGeom();
        if (numBox.getX() != g[0]) {
            numBox.setX(g[0]);
        }
        if (numBox.getY() != g[1]) {
            numBox.setY(g[1]);
        }
        if (numBox.getWidth() != g[2] || numBox.getHeight() != g[3]) {
            ensureNumBox(); // 尺寸变了（极少见）→ 重建
        }
    }

    // ========================================================================
    // 一行：界面尺寸
    // ========================================================================

    /** 行顶部 y（屏幕坐标） */
    private int rowTopY() {
        return panelY + ROWS_TOP + ROW_H + ROW_GAP;
    }

    private int modeTopY() {
        return panelY + ROWS_TOP;
    }

    private void renderModeRow(GuiGraphics gui, int mouseX, int mouseY) {
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        int y = modeTopY();
        boolean adaptive = org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive();
        gui.drawString(parent.font(), t("settings_gui_mode"), panelX + PAD, y + 4, C_LABEL, false);
        int bx = panelX + SLIDER_X;
        int bw = PLUS_X + ARROW_W - SLIDER_X;
        boolean hovered = isIn(mouseX, mouseY, bx, y, bw, ROW_H);
        gui.fill(overlay, bx, y, bx + bw, y + ROW_H, hovered ? 0xFF3A6EA5 : 0xFF24476E);
        // ★ 2026-09-21：无投影（drawCenteredString 固定带阴影 → 自己算居中）
        var modeFont = parent.font();
        String modeText = t(adaptive ? "settings_gui_adaptive" : "settings_gui_manual");
        gui.drawString(modeFont, modeText, bx + (bw - modeFont.width(modeText)) / 2, y + 4, 0xFFFFFFFF, false);
    }

    /** 绘制「界面尺寸」行 */
    private void renderGuiScaleRow(GuiGraphics gui, int mouseX, int mouseY) {
        var font = parent.font();
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        final int y = rowTopY();
        final int pct = getScalePct();
        if (org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive()) {
            gui.drawString(font, t("settings_gui_auto_hint"), panelX + SLIDER_X, y + 4, C_HINT, false);
            return;
        }

        // 标签（垂直居中）
        gui.drawString(font, t("settings_gui_scale"),
                panelX + PAD, y + (ROW_H - font.lineHeight) / 2, C_LABEL, false);

        // ---- 进度条（胶囊：轨道 + 填充 + 手柄）----
        final int sx = panelX + SLIDER_X;
        final int sy = y + (ROW_H - SLIDER_H) / 2;
        fillRounded(gui, sx, sy, sx + SLIDER_W, sy + SLIDER_H, SLIDER_H / 2, C_TRACK);
        final int fillW = Math.max(1, (int) Math.round(SLIDER_W * fraction(pct)));
        fillRounded(gui, sx, sy, sx + fillW, sy + SLIDER_H, SLIDER_H / 2, C_FILL);
        final int hx = sx + fillW;
        fillRounded(gui, hx - 3, sy - 2, hx + 3, sy + SLIDER_H + 2, 3, 0xCC202028);
        fillRounded(gui, hx - 2, sy - 1, hx + 2, sy + SLIDER_H + 1, 2, C_HANDLE);

        // ---- − / + 箭头 ----
        drawArrowButton(gui, mouseX, mouseY, panelX + MINUS_X, y, "−");
        drawArrowButton(gui, mouseX, mouseY, panelX + PLUS_X, y, "+");

        // ---- 数字框自绘底色/描边（输入控件本体在 render 末尾画）----
        final int nx = panelX + NUM_X;
        final int ny = y + (ROW_H - NUM_H) / 2;
        gui.fill(overlay, nx, ny, nx + NUM_W, ny + NUM_H, C_NUM_BG);
        gui.fill(overlay, nx, ny, nx + NUM_W, ny + 1, C_NUM_EDGE);
        gui.fill(overlay, nx, ny + NUM_H - 1, nx + NUM_W, ny + NUM_H, C_NUM_EDGE);
        gui.fill(overlay, nx, ny, nx + 1, ny + NUM_H, C_NUM_EDGE);
        gui.fill(overlay, nx + NUM_W - 1, ny, nx + NUM_W, ny + NUM_H, C_NUM_EDGE);
        // 聚焦时描边换成亮蓝（与整体强调色一致）
        if (numBox != null && numBox.isFocused()) {
            gui.fill(overlay, nx, ny, nx + NUM_W, ny + 1, C_FILL);
            gui.fill(overlay, nx, ny + NUM_H - 1, nx + NUM_W, ny + NUM_H, C_FILL);
            gui.fill(overlay, nx, ny, nx + 1, ny + NUM_H, C_FILL);
            gui.fill(overlay, nx + NUM_W - 1, ny, nx + NUM_W, ny + NUM_H, C_FILL);
        }
        // ⚠️ 数值文字【只由 numBox 画】—— 本方法不再自绘。
        //   早先这里额外画了一层 `pct + "%"`，而 EditBox 内部始终持有同一个值
        //   （syncNumBoxText 写入），两者叠加就成了「两重显示」。
        //   现在删掉自绘，只保留 EditBox 那一层（它同时负责光标与选区高亮）。
    }

    /** 一个方形箭头按钮（−/+） */
    private void drawArrowButton(GuiGraphics gui, int mouseX, int mouseY, int x, int y, String glyph) {
        final int by = y + (ROW_H - ARROW_W) / 2;
        final boolean hovered = isIn(mouseX, mouseY, x, by, ARROW_W, ARROW_W);
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        gui.fill(overlay, x, by, x + ARROW_W, by + ARROW_W, hovered ? 0xFF3A6EA5 : 0xFF24476E);
        // ★ 2026-09-21：无投影
        var arrowFont = parent.font();
        gui.drawString(arrowFont, glyph, x + (ARROW_W - arrowFont.width(glyph)) / 2, by + 4, C_ARROW, false);
    }

    // ========================================================================
    // 取值（50~200，基准 100）
    // ========================================================================

    /** 当前设置值（相对基准的偏移量：0 = 基准） */
    private int getScalePct() {
        return org.zifeng.skilltree.client.SkillKeyBinds.getSkillTreeGuiOffset();
    }

    /** 写入设置值（自动钳制到 -50~+100 并持久化） */
    private void setScalePct(int off) {
        final int clamped = Math.max(MIN_OFF, Math.min(MAX_OFF, off));
        if (clamped == getScalePct()) {
            return;
        }
        org.zifeng.skilltree.client.SkillKeyBinds.setSkillTreeGuiOffset(clamped);
        syncNumBoxText();
    }

    /** 值 → 进度条比例 0~1 */
    private static float fraction(int off) {
        return (off - MIN_OFF) / (float) (MAX_OFF - MIN_OFF);
    }

    /** 进度条 x 坐标 → 值（拖动用） */
    private int pctFromSliderX(double mouseX) {
        final double rel = (mouseX - (panelX + SLIDER_X)) / (double) SLIDER_W;
        final double f = Math.max(0.0, Math.min(1.0, rel));
        return (int) Math.round(MIN_OFF + f * (MAX_OFF - MIN_OFF));
    }

    /**
     * 数值显示格式：正数带 {@code +}，0 与负数原样。
     * <p>用户设定「支持 +1 加大、-1 缩小」→ 正负号必须可见。
     */
    private static String fmtOffset(int off) {
        return off > 0 ? "+" + off : String.valueOf(off);
    }

    // ========================================================================
    // 数字输入框
    // ========================================================================

    /**
     * 数值输入框几何（屏幕坐标）。
     *
     * <p>★ 2026-09-21：返回的是 <b>{@link SkillSearchBox} 的内层几何</b>。
     * 该控件自带 {@link SkillSearchBox#PAD_X}/{@link SkillSearchBox#PAD_Y} 内边距
     * （内层坐标 = 传入坐标 + 内边距），把内边距在此抵掉之后，
     * 文字才会落在与原来（原版 EditBox, bordered=false）逐像素相同的位置上。
     */
    private int[] numBoxGeom() {
        final int y = rowTopY();
        return new int[]{panelX + NUM_X + 1 - SkillSearchBox.PAD_X,
                y + (ROW_H - NUM_H) / 2 + 2 - SkillSearchBox.PAD_Y,
                NUM_W - 2 + SkillSearchBox.PAD_X,
                NUM_H - 4 + SkillSearchBox.PAD_Y * 2};
    }

    /** 创建/复用输入框（几何变化时重建，同 SkillTreeScreen.ensureSearchBox 做法） */
    private void ensureNumBox() {
        int[] g = numBoxGeom();
        if (numBox != null && numBox.getX() == g[0] && numBox.getY() == g[1]
                && numBox.getWidth() == g[2] && numBox.getHeight() == g[3]) {
            return; // 几何未变：复用（保住光标与选区状态）
        }
        // ★ 2026-09-21：改用 SkillSearchBox（无投影输入框）。
        //   原因：原版 EditBox.renderWidget 内部固定用带投影的 drawString（无开关），
        //   而用户要求全局统一「不要阴影」。底色/描边本来就由本界面自绘（原 setBordered(false)），
        //   而 SkillSearchBox 构造时就会 setBordered(false)，因此这里不再单独设置。
        numBox = new SkillSearchBox(parent.font(), g[0], g[1], g[2], g[3],
                0, "", C_NUM_TEXT, C_NUM_TEXT);
        numBox.setMaxLength(4);                 // "+100" / "-50" 最多四位
        // 允许数字、以及开头的正负号（中间不允许符号；允许暂空，便于清空重输）
        numBox.setFilter(s -> s.matches("[+-]?\\d*"));
        // 注意：不注册 responder —— 本面板的重心是「提交时校验」（回车/失焦/关闭），
        // 而不是边输边钳制（那会让玩家输 "5" 想打 "50" 时被立刻夹到 50）。
        syncNumBoxText();
    }

    /** 把当前设置值写回输入框（带符号显示） */
    private void syncNumBoxText() {
        if (numBox == null) {
            return;
        }
        numBox.setValue(fmtOffset(getScalePct()));
    }

    /** 提交输入框内容（解析 → 钳制 → 落盘 → 归一化显示） */
    private void commitNumBox() {
        if (numBox == null) {
            return;
        }
        final String raw = numBox.getValue().trim();
        // 允许玩家输入 "+100"；单独一个符号/空串视为无效，下面统一写回规范值
        if (raw.matches("[+-]?\\d+")) {
            try {
                setScalePct(Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                // 极小概率的超长数字 → 丢弃，下面统一写回规范值
            }
        }
        syncNumBoxText(); // 空值/非法值都会被纠正回当前值
    }

    /** 失焦（同时提交） */
    private void blurNumBox() {
        if (numBox != null && numBox.isFocused()) {
            commitNumBox();
            numBox.setFocused(false);
        }
    }

    /** 让输入框获得焦点 */
    private void focusNumBox() {
        if (numBox != null) {
            numBox.setFocused(true);
            numBox.setCursorPosition(numBox.getValue().length());
        }
    }

    // ========================================================================
    // 交互
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // 标题栏拖动
        }
        if (button == 0 && isCloseBtnHit(mouseX, mouseY)) {
            blurNumBox();
            parent.closeSubScreen();
            return true;
        }
        if (button != 0) {
            return true; // 拦截右键等，不穿透到下层（区域隔离）
        }
        final int y = rowTopY();
        if (button == 0 && isIn(mouseX, mouseY, panelX + PAD, modeTopY(), PANEL_W - PAD * 2, ROW_H)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setSkillTreeGuiAdaptive(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive());
            syncNumBoxText();
            return true;
        }
        if (org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive()) {
            return true;
        }

        // 数字输入框：先给自己一次机会（点击定位光标）
        if (numBox != null) {
            final boolean overNumBox = isIn(mouseX, mouseY, panelX + NUM_X,
                    y + (ROW_H - NUM_H) / 2, NUM_W, NUM_H);
            if (overNumBox) {
                focusNumBox();
                numBox.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        // 点面板其它地方 → 输入框失焦（提交）
        blurNumBox();

        // − / + 箭头（普通=1、Shift=10、Ctrl=100）
        final int step = Screen.hasControlDown() ? 100 : (Screen.hasShiftDown() ? 10 : 1);
        if (isIn(mouseX, mouseY, panelX + MINUS_X, y + (ROW_H - ARROW_W) / 2, ARROW_W, ARROW_W)) {
            setScalePct(getScalePct() - step);
            return true;
        }
        if (isIn(mouseX, mouseY, panelX + PLUS_X, y + (ROW_H - ARROW_W) / 2, ARROW_W, ARROW_W)) {
            setScalePct(getScalePct() + step);
            return true;
        }
        // 进度条：点击即跳值并进入拖动
        //  ★ 2026-09-21：本面板已脱离缩放（1:1 渲染）→ 拖动坐标天然恒定，无需冻结任何东西
        if (isIn(mouseX, mouseY, panelX + SLIDER_X, y + (ROW_H - SLIDER_H) / 2 - 2,
                SLIDER_W, SLIDER_H + 4)) {
            draggingSlider = true;
            setScalePct(pctFromSliderX(mouseX));
            return true;
        }
        return true; // 面板内其余区域也吞掉（区域隔离：不穿透到下层技能树）
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider && button == 0) {
            setScalePct(pctFromSliderX(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = false;
        super.mouseReleased(mouseX, mouseY, button); // 基类：结束标题栏拖动并保存位置
    }

    /** 滚轮：本面板暂不响应，但必须吞掉（区域隔离 → 不穿透去滚动下层技能列表） */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (numBox != null && numBox.isFocused()) {
            final int esc = org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
            final int enter = org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
            final int kpEnter = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
            if (keyCode == enter || keyCode == kpEnter) {
                commitNumBox();
                numBox.setFocused(false);
                return true;
            }
            if (keyCode == esc) {
                syncNumBoxText();  // Esc = 放弃本次输入，恢复原值
                numBox.setFocused(false);
                return true;
            }
            if (numBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 未消费的按键一律吞掉：否则会透传触发界面快捷键（如 E 关界面）
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (numBox != null && numBox.isFocused() && numBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        // 关面板前提交未确认的输入（避免"输完直接点 ✕"丢失）
        if (numBox != null && numBox.isFocused()) {
            numBox.setFocused(false);
            commitNumBox();
        }
        draggingSlider = false;
        super.onClose();
    }

    /** 矩形命中判定 */
    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
