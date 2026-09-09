package org.zifeng.skilltree.client;
import net.minecraft.network.chat.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

/**
 * 凤凰涅槃冷却 HUD 提示（2026-09-08 改版：并入技能点 HUD 模块正下方显示）：
 * <ul>
 *   <li>位置：技能点 HUD（左下角模块）正下方，随模块显隐/偏移，不再悬浮经验条上方</li>
 *   <li>图标：不死图腾（凤凰涅槃图标同款）</li>
 *   <li>冷却中：图标暗色遮罩 + 剩余秒数；冷却就绪：亮色闪烁 + 「就绪」文字</li>
 *   <li>未学/关闭/技能重置：图标隐藏（服务端在学→未学时补发 false，2026-09-08 修复）</li>
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

    /** 客户端每 tick 本地递减倒计时（服务端每秒校准一次 → 秒数平滑准确，2026-09-08 修复计时不准） */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (learned && cooldownRemainingTicks > 0) {
            cooldownRemainingTicks--;
        }
    }

    /**
     * 由技能点 HUD 模块调用：在 (x, y) 处绘制不死图腾图标 + 冷却倒计时 / 就绪提示。
     * 模块已统一处理 hudVisible / 旁观 / 开界面隐藏，此处只画内容。
     */
    public static void renderInto(GuiGraphics gui, int x, int y) {
        if (!learned) {
            return; // 未学/已重置/已关闭：隐藏
        }
        Minecraft mc = Minecraft.getInstance();
        // 不死图腾图标 16×16
        gui.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
        if (cooldownRemainingTicks > 0) {
            // 冷却中：暗色遮罩 + 剩余秒数
            gui.fill(x, y, x + 16, y + 16, 0x88000000);
            int seconds = (cooldownRemainingTicks + 19) / 20;
            gui.drawString(mc.font, seconds + "s", x + 18, y + 4, 0xFFFFAA55);
        } else {
            // 冷却就绪：亮色描边闪烁 + 「就绪」文字
            int flash = (int) ((System.currentTimeMillis() / 500) % 2);
            int c = flash == 0 ? 0xFFFFFF55 : 0xFFFFAA00;
            gui.fill(x - 1, y - 1, x + 17, y + 17, c);
            gui.fill(x, y, x + 16, y + 16, 0x22000000);
            gui.drawString(mc.font, net.minecraft.network.chat.Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.revive_ready").getString(), x + 18, y + 4, c);
        }
    }
}
