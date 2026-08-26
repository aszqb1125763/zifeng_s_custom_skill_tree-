package org.zifeng.skilltree.client.screen;

/**
 * 技能按钮（独立类，避免内部类在 NeoForge 模块加载器下的 NoClassDefFoundError）。
 */
public record SkillButton(String skillId, int x, int y) {

    /** 按钮尺寸（与 SkillTreeScreen 常量保持一致） */
    public static final int WIDTH = 150;
    public static final int HEIGHT = 46;

    public boolean isHovered(double mouseX, double mouseY, SkillTreeScreen screen) {
        double px = screen.toPanelX(mouseX);
        double py = screen.toPanelY(mouseY);
        return px >= x && px <= x + WIDTH && py >= y && py <= y + HEIGHT;
    }
}
