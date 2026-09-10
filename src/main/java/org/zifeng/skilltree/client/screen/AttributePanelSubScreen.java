package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 属性面板子界面（2026-09-01 子界面系统）：
 * <p>显示在原右侧属性面板位置（右侧竖版，带滚动条），内容与旧属性面板一致（实时属性值 + 技能点）。
 * <p>由主界面右下角原开关按钮打开；右上角 ✕ / ESC 关闭；再点原开关按钮也可切换关闭。
 * <p>不透明面板样式（自研），避免半透明叠加暗色接缝。
 */
public class AttributePanelSubScreen extends SkillSubScreen {
    /** 面板宽度（与主界面 PANEL_WIDTH 一致） */
    private static final int PANEL_WIDTH = 200;
    /** 滚动偏移（0 = 顶部） */
    private int scroll = 0;

    public AttributePanelSubScreen(SkillTreeScreen parent) {
        super(parent);
        this.posKey = "attribute_panel";
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        // 右侧竖版：x 贴右缘，y 从 50 到底部 -30（与旧面板区域一致）
        panelW = PANEL_WIDTH;
        panelH = screenHeight - 30 - 50;
        panelX = screenWidth - PANEL_WIDTH - 10;
        panelY = 50;
        // 恢复上次拖动的位置（有保存值则覆盖默认）
        super.init(screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        renderPanelBase(gui, t("panel_title"), mouseX, mouseY);
        renderSourceButton(gui, mouseX, mouseY);
        renderRows(gui, mouseX, mouseY);
        if (isSourceBtnHit(mouseX, mouseY)) {
            renderSourceTooltip(gui, mouseX, mouseY); // 悬停提示：最后绘制（最上层）
        }
    }

    // ============ 数据源切换按钮（2026-09-11：标题栏右侧，✕ 左边） ============

    /** 按钮尺寸（放在标题栏内，高 14 与标题栏 20 协调） */
    private static final int SRC_BTN_W = 62;
    private static final int SRC_BTN_H = 14;

    private int srcBtnX() {
        // ✕（12px + 右边距 4）+ 4px 间隔 → 按钮右缘
        return panelX + panelW - CLOSE_BTN_SIZE - 8 - SRC_BTN_W;
    }

    private int srcBtnY() {
        return panelY + 3;
    }

    private boolean isSourceBtnHit(double mouseX, double mouseY) {
        return mouseX >= srcBtnX() && mouseX <= srcBtnX() + SRC_BTN_W
                && mouseY >= srcBtnY() && mouseY <= srcBtnY() + SRC_BTN_H;
    }

    /** 数据源按钮：蓝=读原版实时值 / 橙=仅技能计算值（用配色区分状态，一眼可辨） */
    private void renderSourceButton(GuiGraphics gui, int mouseX, int mouseY) {
        boolean vanilla = org.zifeng.skilltree.client.SkillKeyBinds.isPanelUseVanillaAttr();
        boolean hovered = isSourceBtnHit(mouseX, mouseY);
        int x = srcBtnX();
        int y = srcBtnY();
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        int bg = vanilla ? (hovered ? 0xFF2A6A8A : 0xFF1E4A5E)
                         : (hovered ? 0xFF6A4A22 : 0xFF4A3418);
        int border = vanilla ? 0xFF66CCFF : 0xFFD8A860;
        int text = vanilla ? 0xFFAEE6FF : 0xFFF0C888;
        gui.fill(overlay, x, y, x + SRC_BTN_W, y + SRC_BTN_H, bg);
        gui.fill(overlay, x, y, x + SRC_BTN_W, y + 1, border);
        gui.fill(overlay, x, y + SRC_BTN_H - 1, x + SRC_BTN_W, y + SRC_BTN_H, border);
        gui.fill(overlay, x, y, x + 1, y + SRC_BTN_H, border);
        gui.fill(overlay, x + SRC_BTN_W - 1, y, x + SRC_BTN_W, y + SRC_BTN_H, border);
        gui.drawCenteredString(parent.font(), t(vanilla ? "panel_src_vanilla" : "panel_src_skill"),
                x + SRC_BTN_W / 2, y + 3, text);
    }

    /** 按钮悬停提示（简短两行：一行说明作用，一行说明差别） */
    private void renderSourceTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        var font = parent.font();
        String l1 = t("panel_src_tip1");
        String l2 = t("panel_src_tip2");
        int w = Math.max(font.width(l1), font.width(l2)) + 8;
        int h = 21;
        int x = Math.max(2, Math.min(mouseX + 8, screenW - w - 2));
        int y = Math.max(2, Math.min(mouseY + 12, screenH - h - 2));
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        gui.fill(overlay, x, y, x + w, y + h, 0xF0101418);
        gui.fill(overlay, x, y, x + w, y + 1, 0xFF87CEEB);
        gui.fill(overlay, x, y + h - 1, x + w, y + h, 0xFF87CEEB);
        gui.fill(overlay, x, y, x + 1, y + h, 0xFF87CEEB);
        gui.fill(overlay, x + w - 1, y, x + w, y + h, 0xFF87CEEB);
        gui.drawString(font, l1, x + 4, y + 2, 0xFFFFD700);
        gui.drawString(font, l2, x + 4, y + 11, 0xFFAAAAAA);
    }

    /** 行高（与原实现一致，本次改动仅在视觉层） */
    private static final int ROW_H = 12;
    /** 底部提示条高度（常驻数据源说明 + 滚动提示） */
    private static final int BOTTOM_BAR_H = 22;

    /**
     * 渲染属性行（2026-09-11 视觉优化，参考单机游戏属性面板）：
     * <ul>
     *   <li>分组标题 → 底色条 + 左侧金色强调块（替代原来「—— 战斗 ——」纯文字）</li>
     *   <li>正文行 → 斑马纹隔行浅底，长列表更易扫读</li>
     *   <li>底部提示条 → 左侧常驻「数据源」说明 + 右侧滚动提示</li>
     * </ul>
     */
    private void renderRows(GuiGraphics gui, int mouseX, int mouseY) {
        var font = parent.font();
        var rows = parent.collectRows();
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();

        // 滚动区域：标题栏下方 → 底部提示条上方
        int scrollTop = panelY + TITLE_BAR_H + 3;
        int scrollBottom = panelY + panelH - BOTTOM_BAR_H;
        int visible = Math.max(1, (scrollBottom - scrollTop) / ROW_H);
        int maxScroll = Math.max(0, rows.size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        // 列表底（比面板稍深，形成"内容区"层次感）
        gui.fill(overlay, panelX + 5, scrollTop - 2, panelX + panelW - 5, scrollBottom + 1, 0xFF0E1218);

        int line = scrollTop;
        int stripe = 0;
        for (int i = scroll; i < rows.size() && i < scroll + visible; i++) {
            String[] row = rows.get(i);
            String color = row.length > 2 ? row[2] : "#FFFFFFFF";
            int c = SkillTreeScreen.parseColor(color);
            if (row[1].isEmpty()) {
                // 分组标题：底色条 + 左侧金色强调块 + 左对齐标题
                gui.fill(overlay, panelX + 7, line - 1, panelX + panelW - 7, line + ROW_H - 1, 0xFF1F2C3A);
                gui.fill(overlay, panelX + 7, line - 1, panelX + 9, line + ROW_H - 1, 0xFFFFD700);
                String section = row[0].replace("——", "").trim();
                gui.drawString(font, section, panelX + 12, line, c);
                stripe = 0; // 分组后重新计斑马纹
            } else {
                if ((stripe & 1) == 1) {
                    gui.fill(overlay, panelX + 7, line - 1, panelX + panelW - 7, line + ROW_H - 1, 0x0FFFFFFF);
                }
                stripe++;
                String label = row[0];
                int labelMaxW = panelW - 78;
                while (!label.isEmpty() && font.width(label) > labelMaxW) {
                    label = label.substring(0, label.length() - 1);
                }
                gui.drawString(font, label, panelX + 11, line, 0xFFB0B0B0);
                String value = row[1];
                gui.drawString(font, value, panelX + panelW - 12 - font.width(value), line, c);
            }
            line += ROW_H;
        }

        // 右侧滚动条（轨道 + 滑块）
        int barX = panelX + panelW - 6;
        int barTrackTop = scrollTop;
        int barTrackBottom = scrollBottom - 1;
        int barTrackH = Math.max(1, barTrackBottom - barTrackTop);
        gui.fill(overlay, barX, barTrackTop, barX + 2, barTrackBottom, 0xFF2A2F36);
        if (maxScroll > 0) {
            int thumbH = Math.max(10, barTrackH * visible / rows.size());
            int thumbY = barTrackTop + (int) ((double) scroll / maxScroll * (barTrackH - thumbH));
            gui.fill(overlay, barX, thumbY, barX + 2, thumbY + thumbH, 0xFF87CEEB);
        } else {
            gui.fill(overlay, barX, barTrackTop, barX + 2, barTrackBottom, 0xFF3E5A73);
        }

        // 底部提示条：分隔线 + 左（数据源说明）右（滚动提示）
        int barTop = panelY + panelH - BOTTOM_BAR_H;
        gui.fill(overlay, panelX + 4, barTop, panelX + panelW - 4, barTop + 1, 0x554488AA);
        gui.drawString(font, t("panel_src_hint"), panelX + 7, barTop + 7, 0xFF7F9AB0);
        if (maxScroll > 0) {
            String sh = t("panel_scroll_hint");
            gui.drawString(font, sh, panelX + panelW - 9 - font.width(sh), barTop + 7, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 数据源按钮优先：它位于标题栏内，必须在「标题栏拖动」判定之前拦截，否则会一边切换一边拖面板
        if (button == 0 && isSourceBtnHit(mouseX, mouseY)) {
            org.zifeng.skilltree.client.SkillKeyBinds.togglePanelUseVanillaAttr();
            return true;
        }
        // 基类：左键点中标题栏 → 标记拖动（排除 ✕）
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isCloseBtnHit(mouseX, mouseY)) {
            parent.closeSubScreen();
            return true;
        }
        return true; // 面板内点击一律拦截（不透传到技能树）
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 滚轮滚动（Shift=快速 5 行）
        int step = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 5 : 1;
        scroll -= (int) (delta * step);
        return true;
    }
}
