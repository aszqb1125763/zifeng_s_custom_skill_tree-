package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 木棍工具·当前功能小面板（2026-09-08 重做，NeoForge 1.21.1）：
 * 主手或副手持木棍 + 已学任一工具技能时，右下角紧凑显示【当前功能】：
 * <ul>
 *   <li>工具开：两行——首行彩色圆点+功能名（如「磁铁隔离选区」）；次行一行核心操作提示</li>
 *   <li>工具关：一行灰字「木棍已还原原版」</li>
 * </ul>
 * 切换模式另有 actionbar 即时反馈（见 ModKeyBindingEvents.feedbackStickMode）。
 */
public final class StickToolHudRenderer {
    private StickToolHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        // ⚠️ 2026-09-08：主手或副手任一木棍均显示（副手木棍只做状态/选区查看，不抢交互）
        // ★ 2026-10-09：判定改为「木棍本体 或 已链接物品类型」
        if (!ModKeyBindingEvents.matchesStickGestureClient(mc.player.getMainHandItem())
                && !ModKeyBindingEvents.matchesStickGestureClient(mc.player.getOffhandItem())) {
            return;
        }
        if (!ModKeyBindingEvents.hasAnyStickToolSkillClient()) {
            return; // 未学任何木棍工具技能：木棍纯原版，无提示
        }
        boolean on = ModKeyBindingEvents.isStickToolOnClient();
        int mode = ModKeyBindingEvents.getStickToolModeClient();
        // 当前模式可能因切换停留在未解锁模式（退出时缓存模式保留）→ 用已解锁显示名/提示兜底
        if (on && !ModKeyBindingEvents.isStickModeUnlocked(mode)) {
            mode = ModKeyBindingEvents.nextUnlockedStickMode(mode);
        }

        String title;
        int titleColor;
        String sub = null;
        net.minecraft.world.item.ItemStack icon = null;
        if (on) {
            // 首行：模式图标 + 功能名（切换后一眼看到对应功能）
            title = Component.translatable(
                    "ui.zifeng_s_custom_skill_tree." + StickToolModes.modeLang(mode)).getString();
            titleColor = StickToolModes.colorOfMode(mode);
            icon = new net.minecraft.world.item.ItemStack(StickToolModes.iconOfMode(mode));
            // 次行：一行核心操作提示
            sub = Component.translatable(
                    "ui.zifeng_s_custom_skill_tree." + StickToolModes.hintLang(mode)).getString();
        } else {
            title = Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.stick_tool_hint_off").getString();
            titleColor = 0xFFAAAAAA;
        }

        GuiGraphics gg = event.getGuiGraphics();
        var font = mc.font;
        // 右下角对齐；距底部 35 像素，右缘留 4 像素（2026-09-08）
        int right = mc.getWindow().getGuiScaledWidth() - 4;
        int bottom = mc.getWindow().getGuiScaledHeight() - 35;
        int pad = 4;
        // 标题过长截断（英文窄屏保护）
        while (font.width(title) > 180) {
            title = title.substring(0, title.length() - 1);
        }
        // ★ 2026-10-11：模式图标占 12×12（物品图标 16×16 缩放），标题文字右移让位
        final int iconSize = 12;
        final int iconGap = 3;
        final int iconW = icon != null ? iconSize + iconGap : 0;
        int maxW = font.width(title) + iconW;
        if (sub != null) {
            maxW = Math.max(maxW, font.width(sub));
        }
        int bgX = right - maxW - pad * 2;
        int lines = sub != null ? 2 : 1;
        int totalH = lines * 10 + pad * 2 - 4;
        gg.fill(bgX - 1, bottom - totalH - 1, right + 1, bottom + 1, 0xAA000000);
        int y = bottom - totalH + pad - 2;
        if (icon != null) {
            // 图标垂直居中于首行文字（文字高 9px，图标 12px → 上移 1~2px 对齐）
            var pose = gg.pose();
            pose.pushPose();
            pose.translate(bgX + pad, y - 2, 0);
            pose.scale(iconSize / 16.0F, iconSize / 16.0F, 1.0F);
            gg.renderItem(icon, 0, 0);
            pose.popPose();
        }
        gg.drawString(font, title, bgX + pad + iconW, y, titleColor, false);
        if (sub != null) {
            gg.drawString(font, sub, bgX + pad, y + 10, 0xFFDDDDDD, false);
        }
    }
}
