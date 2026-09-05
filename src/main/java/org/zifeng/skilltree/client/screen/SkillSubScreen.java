package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 技能树子界面基类（2026-09-01 新增子界面系统）：
 * <p>主界面（技能树）之上悬浮的独立功能面板。子界面可无限扩展，每个子界面是一个独立功能模块。
 * <p>渲染时序：主界面五层（L5背景→L4技能树本体→L3tooltip→L2边框→L1面板标题）渲染完成后，
 * 最后渲染当前打开的子界面（最上层，不透明面板覆盖一切）。
 * <p>交互：子界面打开时，鼠标点击/滚轮/键盘优先交给子界面；ESC 逐层关闭（先关子界面，再关主界面）。
 * <p>面板样式：自研不透明圆角面板（深色背景 + 淡蓝边框 + 金色标题 + 右上角 ✕ 关闭按钮），
 * 不透明填充避免半透明矩形叠加产生的暗色接缝问题。
 */
public abstract class SkillSubScreen {
    /** 父界面（主技能树界面） */
    protected final SkillTreeScreen parent;
    /** 面板矩形（屏幕坐标） */
    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;

    /** 关闭按钮区域大小 */
    protected static final int CLOSE_BTN_SIZE = 12;
    /** 标题栏高度 */
    protected static final int TITLE_BAR_H = 20;
    /** 圆角半径 */
    protected static final int PANEL_RADIUS = 6;

    protected SkillSubScreen(SkillTreeScreen parent) {
        this.parent = parent;
    }

    /** 子界面位置持久化 key（子类设置，如 "attribute_panel"/"hud_adjust"；null = 不持久化位置） */
    protected String posKey = null;
    /** 是否正在通过标题栏拖动面板（左键，2026-09-01） */
    private boolean draggingByTitle = false;
    /** 拖动锚点：按下时鼠标位置 + 面板位置（保证拖动时鼠标相对面板静止，不漂移） */
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartPanelX;
    private int dragStartPanelY;
    /** 屏幕尺寸（init 时记录，拖动边界用） */
    protected int screenW = 0;
    protected int screenH = 0;

    /** 打开子界面时调用：初始化面板几何（屏幕尺寸）。子类设置默认位置后调 super.init 恢复上次位置。 */
    public void init(int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        if (posKey != null) {
            int[] saved = org.zifeng.skilltree.client.SkillKeyBinds.getSubScreenPos(posKey);
            if (saved != null) {
                panelX = saved[0];
                panelY = saved[1];
                clampPos(); // 旧保存值可能出界，恢复时钳制
            }
        }
    }

    /** 渲染子界面（背景 + 标题 + 内容）。每帧调用。 */
    public abstract void render(GuiGraphics gui, int mouseX, int mouseY);

    /** 鼠标点击（仅面板内会收到），返回 true = 已处理。基类：左键点中标题栏 → 记录锚点并标记可拖动。 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isTitleBarHit(mouseX, mouseY)) {
            draggingByTitle = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
            return true;
        }
        return false;
    }

    /**
     * 拖动面板（左键按住标题栏拖动，锚点模式）：
     * 面板位置 = 按下时面板位置 + (当前鼠标 - 按下时鼠标) → 鼠标相对面板保持静止，
     * 不会因事件丢失/边界钳制漂移；钳制边界 ≥75% 在屏内。
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingByTitle) {
            panelX = dragStartPanelX + (int) (mouseX - dragStartMouseX);
            panelY = dragStartPanelY + (int) (mouseY - dragStartMouseY);
            clampPos();
            return true;
        }
        return false;
    }

    /** 是否正在拖动面板（供主界面持续转发拖动手势，即使鼠标移出面板） */
    public boolean isDragging() {
        return draggingByTitle;
    }

    /** 鼠标释放（拖动结束保存位置） */
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingByTitle) {
            draggingByTitle = false;
            savePos();
        }
    }

    /** 边界钳制：面板至少 75% 留在屏幕内（最多 25% 出屏），避免拖出屏幕后卡住 */
    private void clampPos() {
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        int minX = -(int) (panelW * 0.25);
        int maxX = screenW - (int) (panelW * 0.75);
        int minY = -(int) (panelH * 0.25);
        int maxY = screenH - (int) (panelH * 0.75);
        if (minX > maxX) {
            minX = maxX = (screenW - panelW) / 2;
        }
        if (minY > maxY) {
            minY = maxY = (screenH - panelH) / 2;
        }
        panelX = Math.max(minX, Math.min(maxX, panelX));
        panelY = Math.max(minY, Math.min(maxY, panelY));
    }

    /** 是否点中标题栏区域（可拖动区域 = 标题栏，排除 ✕ 关闭按钮） */
    protected boolean isTitleBarHit(double mouseX, double mouseY) {
        return mouseY >= panelY && mouseY <= panelY + TITLE_BAR_H
                && mouseX >= panelX && mouseX <= panelX + panelW
                && !isCloseBtnHit(mouseX, mouseY);
    }

    /** 保存面板位置（持久化，重启恢复） */
    protected void savePos() {
        if (posKey != null) {
            org.zifeng.skilltree.client.SkillKeyBinds.setSubScreenPos(posKey, panelX, panelY);
        }
    }

    /** 滚轮（面板内悬停时调用），返回 true = 已处理 */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** 键盘（子界面打开时优先收到），返回 true = 已处理。基类处理 ESC 关闭子界面。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            parent.closeSubScreen();
            return true;
        }
        return false;
    }

    /** 鼠标是否在面板矩形内 */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    /** 子界面关闭时调用（可重写做清理）。基类保存位置。 */
    public void onClose() {
        savePos();
    }

    // ============ 自研不透明面板样式（渲染骨架） ============

    /** 渲染面板骨架：不透明圆角背景 + 淡蓝边框 + 金色标题 + 右上角 ✕ 关闭按钮 */
    protected void renderPanelBase(GuiGraphics gui, String title, int mouseX, int mouseY) {
        // 不透明背景（深灰蓝，无 alpha → 不会出现半透明叠加暗色接缝）
        fillRounded(gui, panelX, panelY, panelX + panelW, panelY + panelH, PANEL_RADIUS, 0xFF151A20);
        // 淡蓝边框（外扩 1px，不透明）
        fillRounded(gui, panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_RADIUS + 1, 0xFF87CEEB);
        // 再画一层背景盖住边框内侧（边框仅保留外圈）
        fillRounded(gui, panelX, panelY, panelX + panelW, panelY + panelH, PANEL_RADIUS, 0xFF151A20);
        // 标题（金色，左上角）
        Font font = parent.font();
        gui.drawString(font, title, panelX + 6, panelY + 5, 0xFFFFD700);
        // 标题分隔线（标题栏底部，淡蓝 1px）
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), panelX + 4, panelY + TITLE_BAR_H - 1, panelX + panelW - 4, panelY + TITLE_BAR_H, 0xFF87CEEB);
        // 右上角 ✕ 关闭按钮
        renderCloseButton(gui, mouseX, mouseY);
    }

    /** 右上角 ✕ 关闭按钮（不透明） */
    protected void renderCloseButton(GuiGraphics gui, int mouseX, int mouseY) {
        int bx = panelX + panelW - CLOSE_BTN_SIZE - 4;
        int by = panelY + 4;
        boolean hovered = mouseX >= bx && mouseX <= bx + CLOSE_BTN_SIZE && mouseY >= by && mouseY <= by + CLOSE_BTN_SIZE;
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), bx, by, bx + CLOSE_BTN_SIZE, by + CLOSE_BTN_SIZE, hovered ? 0xFF3A6EA5 : 0xFF24476E);
        // 白色 ✕
        int cx = bx + CLOSE_BTN_SIZE / 2;
        int cy = by + CLOSE_BTN_SIZE / 2;
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 3, cy - 1, cx - 1, cy + 1, 0xFFFFFFFF);
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx + 1, cy - 1, cx + 3, cy + 1, 0xFFFFFFFF);
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 1, cy - 3, cx + 1, cy - 1, 0xFFFFFFFF);
        gui.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 1, cy + 1, cx + 1, cy + 3, 0xFFFFFFFF);
    }

    /** 是否命中右上角 ✕ 关闭按钮 */
    protected boolean isCloseBtnHit(double mouseX, double mouseY) {
        int bx = panelX + panelW - CLOSE_BTN_SIZE - 4;
        int by = panelY + 4;
        return mouseX >= bx && mouseX <= bx + CLOSE_BTN_SIZE && mouseY >= by && mouseY <= by + CLOSE_BTN_SIZE;
    }

    /**
     * 自研圆角矩形填充：主体矩形 + 四角多级阶梯近似（3 级，比旧版 2 级更圆滑）。
     * 仅用于不透明颜色（alpha FF），无半透明叠加问题。
     */
    protected static void fillRounded(GuiGraphics gui, int left, int top, int right, int bottom, int radius, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        int r = Math.max(1, Math.min(radius, (right - left) / 2));
        r = Math.min(r, (bottom - top) / 2);
        var type = net.minecraft.client.renderer.RenderType.guiOverlay();
        // 主体
        gui.fill(type, left + r, top, right - r, bottom, color);
        gui.fill(type, left, top + r, right, bottom - r, color);
        // 四角 3 级阶梯
        int s1 = (int) (r * 0.66f); // 最外层阶梯
        int s2 = (int) (r * 0.33f); // 中层阶梯
        // 左上
        gui.fill(type, left, top + s1, left + s1, top + r, color);
        gui.fill(type, left + s1, top + s2, left + s2, top + s1, color);
        gui.fill(type, left + s2, top, left + r, top + s2, color);
        // 右上
        gui.fill(type, right - s1, top + s1, right, top + r, color);
        gui.fill(type, right - s2, top + s2, right - s1, top + s1, color);
        gui.fill(type, right - r, top, right - s2, top + s2, color);
        // 左下
        gui.fill(type, left, bottom - r, left + s1, bottom - s1, color);
        gui.fill(type, left + s1, bottom - s1, left + s2, bottom - s2, color);
        gui.fill(type, left + s2, bottom - s2, left + r, bottom, color);
        // 右下
        gui.fill(type, right - s1, bottom - r, right, bottom - s1, color);
        gui.fill(type, right - s2, bottom - s1, right - s1, bottom - s2, color);
        gui.fill(type, right - r, bottom - s2, right - s2, bottom, color);
    }

    /** 翻译快捷方式（与主界面一致） */
    protected String t(String key) {
        return Component.translatable("ui.zifeng_s_custom_skill_tree." + key).getString();
    }
}
