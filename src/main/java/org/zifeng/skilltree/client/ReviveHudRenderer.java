package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 凤凰涅槃冷却 HUD 提示：
 * <ul>
 *   <li>位置：血条右侧、饥饿条垂直居中（不遮挡经验条与数字）</li>
 *   <li>图标：不死图腾（凤凰涅槃图标同款）</li>
 *   <li>冷却中：灰色半透明 + 剩余秒数；冷却就绪：亮色 + 「就绪」文字闪烁</li>
 *   <li>未学/关闭：图标隐藏（服务端每秒同步 learned=false）</li>
 * </ul>
 */
public class ReviveHudRenderer {

    /** 是否已学且启用（服务端每秒同步） */
    private static boolean learned = false;

    /** 凤凰涅槃冷却剩余 tick（服务端每秒同步，0 = 冷却就绪） */
    private static int cooldownRemainingTicks = 0;

    public static void setCooldown(boolean learnedSkill, int remainingTicks) {
        learned = learnedSkill;
        cooldownRemainingTicks = Math.max(0, remainingTicks);
    }

    /** 客户端当前凤凰涅槃冷却剩余 tick（供 HUD / 其他系统查询） */
    public static int getCooldownTicks() {
        return learned ? cooldownRemainingTicks : 0;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !learned) {
            return;
        }
        // 技能树界面/其他界面打开时不显示（避免遮挡）
        if (mc.screen != null) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        // 位置：经验条中间（水平居中 width/2）、经验数字上方 10 像素。
        // 原版经验条 x：width/2-91 ~ width/2+91（中心=width/2），经验数字 y：height-31；
        // 图标 16px → x = width/2 - 8；y = height - 31 - 10 - 16 = height - 57（图标底边贴经验数字上方 10px）
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 8;
        int y = mc.getWindow().getGuiScaledHeight() - 57;

        // 绘制不死图腾图标（冷却中半透明 + 灰色遮罩）
        gui.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
        if (cooldownRemainingTicks > 0) {
            // 冷却中：加半透明黑遮罩（视觉上变暗）
            gui.fill(x, y, x + 16, y + 16, 0x88000000);
            // 剩余秒数
            int seconds = (cooldownRemainingTicks + 19) / 20;
            String text = seconds + "s";
            gui.drawString(mc.font, text, x + 18, y + 4, 0xFFFFAA55);
        } else {
            // 冷却就绪：亮色描边 + 提示文字（闪烁，避免刺眼）
            int flash = (int) ((System.currentTimeMillis() / 500) % 2);
            gui.fill(x - 1, y - 1, x + 17, y + 17, flash == 0 ? 0xFFFFFF55 : 0xFFFFAA00);
            gui.fill(x, y, x + 16, y + 16, 0x22000000);
            gui.drawString(mc.font, "就绪", x + 18, y + 4, flash == 0 ? 0xFFFFFF55 : 0xFFFFAA00);
        }
    }
}
