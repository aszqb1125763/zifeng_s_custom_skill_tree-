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
        renderRows(gui, mouseX, mouseY);
    }

    /** 渲染属性行（滚动区域 + 滚动条，自研布局） */
    private void renderRows(GuiGraphics gui, int mouseX, int mouseY) {
        var rows = parent.collectRows();
        // 滚动区域：标题栏下方到面板底部（留 20px 提示）
        int scrollTop = panelY + TITLE_BAR_H + 2;
        int scrollBottom = panelY + panelH - 20;
        int visible = (scrollBottom - scrollTop) / 12;
        int maxScroll = Math.max(0, rows.size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        // 滚动区域边框（淡蓝细线，圈出可滚动区域）
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        gui.fill(overlay, panelX + 4, scrollTop - 1, panelX + panelW - 4, scrollTop, 0x554488AA);
        gui.fill(overlay, panelX + 4, scrollBottom, panelX + panelW - 4, scrollBottom + 1, 0x554488AA);

        // 绘制可见行
        int line = scrollTop;
        for (int i = scroll; i < rows.size() && i < scroll + visible; i++) {
            String[] row = rows.get(i);
            String color = row.length > 2 ? row[2] : "#FFFFFFFF";
            int c = SkillTreeScreen.parseColor(color);
            if (row[1].isEmpty()) {
                // 分隔标题行：居中灰色小字
                gui.drawCenteredString(parent.font(), row[0], panelX + panelW / 2, line, c);
            } else {
                String label = row[0];
                int labelMaxW = panelW - 70;
                while (!label.isEmpty() && parent.font().width(label) > labelMaxW) {
                    label = label.substring(0, label.length() - 1);
                }
                gui.drawString(parent.font(), label, panelX + 8, line, 0xFFAAAAAA);
                String value = row[1];
                gui.drawString(parent.font(), value, panelX + panelW - 14 - parent.font().width(value), line, c);
            }
            line += 12;
        }
        // 右侧滚动条（轨道 + 滑块）
        int barX = panelX + panelW - 6;
        int barTrackTop = scrollTop;
        int barTrackBottom = scrollBottom - 2;
        int barTrackH = barTrackBottom - barTrackTop;
        gui.fill(overlay, barX, barTrackTop, barX + 2, barTrackBottom, 0xFF333333);
        if (maxScroll > 0) {
            int thumbH = Math.max(10, barTrackH * visible / rows.size());
            int thumbY = barTrackTop + (int) ((double) scroll / maxScroll * (barTrackH - thumbH));
            gui.fill(overlay, barX, thumbY, barX + 2, thumbY + thumbH, 0xFF87CEEB);
        } else {
            gui.fill(overlay, barX, barTrackTop, barX + 2, barTrackBottom, 0xFF446688);
        }
        if (maxScroll > 0) {
            gui.drawString(parent.font(), t("panel_scroll_hint"), panelX + 8, panelY + panelH - 14, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
