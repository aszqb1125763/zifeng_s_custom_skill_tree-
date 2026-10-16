package org.zifeng.skilltree.client.screen;

/**
 * 技能贴片（独立类，避免内部类在 NeoForge 模块加载器下的 NoClassDefFoundError）。
 *
 * <p>⚠️ 2026-09-19 L4 分区重构：贴片宽度<b>不再固定</b>——它随窗口拉伸
 * （进度条吃弹性空间，Q/E/R 三格贴在贴片右端）。
 * 所以命中判定改为问 {@link SkillTreeScreen#rowW()} 要当前实际宽度，
 * 而不是用本类里的常量（那样窗口一拉宽，判定范围就与画出来的不一致了）。
 *
 * <p>x 恒为 0（原点 = 贴片左上角）；y 逐行递增，以 {@link #HEIGHT} 为步长。
 */
public record SkillButton(String skillId, int x, int y) {

    /** 行高（与 SkillTreeScreen.BUTTON_HEIGHT 一致；宽度是弹性的，见 {@link SkillTreeScreen#rowW()}） */
    public static final int HEIGHT = 28;

    public boolean isHovered(double mouseX, double mouseY, SkillTreeScreen screen) {
        if (!screen.isMouseOverSkillList(mouseX, mouseY)) {
            return false;
        }
        double px = screen.toPanelX(mouseX);
        double py = screen.toPanelY(mouseY);
        return px >= x && px < x + screen.rowW() && py >= y && py < y + HEIGHT;
    }
}
