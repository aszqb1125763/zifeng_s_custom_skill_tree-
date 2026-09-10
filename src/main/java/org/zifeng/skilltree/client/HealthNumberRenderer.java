package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 真实血量数字显示（2026-09-11，随血条压缩一起加入）。
 *
 * <h3>为什么需要它</h3>
 * 血条被等比压缩到最多 10 颗心后（见 {@code GuiHeartsCompressMixin}），
 * 1 颗心代表约 1 万点生命，肉眼无法判断精确血量 → 由本渲染器在血条左侧显示真实数值。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>读原版真实值</b>（{@code player.getHealth()} / {@code getMaxHealth()}），
 *       因此<b>自动兼容任何其他模组加的生命值</b>（装备/药水/其他 mod 的属性修饰符全算进去）</li>
 *   <li><b>完全独立于血条渲染</b>：注册为独立 GUI 层（layered draw）并置于最顶层，
 *       不做任何 Gui Mixin → 与其他血条 mod（ColorfulHearts / OverloadedArmorBar / ClassicBar 等）
 *       零冲突：它们接管血条后显示自己的方案，我们照常显示数字且<b>不会被覆盖</b></li>
 *   <li><b>顺序与原版一致</b>：伤害吸收在<b>上一行</b>，普通生命在<b>下一行</b>
 *       （原版吸收心就是额外画在普通心上方一行）</li>
 *   <li>创造/旁观模式不显示（原版 {@code gameMode.canHurtPlayer() == false} 时压根不画血条，
 *       我们保持一致，避免"只有数字没有血条"的怪异观感）</li>
 * </ul>
 */
public final class HealthNumberRenderer {
    private HealthNumberRenderer() {
    }

    /** 心形条左侧留白（像素） */
    private static final int GAP = 3;

    /** 本模组血量数字的 GUI 层 ID */
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "health_number");

    /**
     * MOD 总线：把血量数字注册为一个 GUI 层。
     * <p><b>层级选择（2026-09-11 用户要求：不超过聊天栏）</b>：注册在
     * {@link VanillaGuiLayers#EXPERIENCE_LEVEL}（第 13 层）之<b>上</b>。
     * <p>NeoForge 1.21.1 的层顺序（前 13 层）：
     * <pre>
     * 1 CAMERA_OVERLAYS  2 CROSSHAIR  3 HOTBAR  4 JUMP_METER  5 EXPERIENCE_BAR
     * 6 PLAYER_HEALTH    7 ARMOR_LEVEL  8 FOOD_LEVEL  9 VEHICLE_HEALTH  10 AIR_LEVEL
     * 11 SELECTED_ITEM_NAME  12 SPECTATOR_TOOLTIP  13 EXPERIENCE_LEVEL
     * </pre>
     * 因此本层位于<b>所有血条相关层（HOTBAR/血条/护甲/饱食/坐骑血）之后</b>
     * （包括 ClassicBar 这类注册在 {@code VEHICLE_HEALTH} 之上的血条 mod），
     * 同时又<b>远低于</b>聊天栏（{@code CHAT} 第 22 层）/标题/副标题/玩家列表 ——
     * 不会盖住聊天等内容。
     */
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, LAYER_ID,
                (guiGraphics, deltaTracker) -> renderHealthNumber(guiGraphics));
    }

    private static void renderHealthNumber(GuiGraphics gui) {
        if (!Config.HEALTH_NUMBER_ENABLED.get()) {
            return; // 功能总开关（配置文件级）
        }
        // HUD 设置面板里的独立开关（玩家级，持久化在 SkillKeyBinds）
        boolean showHealth = SkillKeyBinds.isHudHealthNumber();
        boolean showAbsorption = SkillKeyBinds.isHudAbsorptionNumber();
        boolean showArmor = SkillKeyBinds.isHudArmorNumber();
        if (!showHealth && !showAbsorption && !showArmor) {
            return; // 全关了 → 不渲染
        }
        // 满值显示开关（生命/吸收/护甲三个数字<b>共用</b>；默认关）
        boolean showMax = SkillKeyBinds.isHudShowMaxValue();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        // 与血条压缩共用阈值：真实最大生命没超过原版（20）时不显示数字（原版行为零干扰）
        float realMax = HealthBarHelper.realMaxHealth(mc.player);
        if (!HealthBarHelper.shouldCompress(realMax)) {
            return;
        }
        // 与血条压缩配置联动（关闭压缩时也可以单独开数字，但默认一起开关）
        if (!Config.HEALTH_BAR_COMPRESS.get()) {
            return;
        }
        // 与原文一致：创造/旁观不画血条 → 我们也不画数字
        if (!mc.gameMode.canHurtPlayer()) {
            return;
        }

        float realHealth = HealthBarHelper.realHealth(mc.player);
        float realAbsorption = HealthBarHelper.realAbsorption(mc.player);
        int realArmor = HealthBarHelper.realArmor(mc.player);

        // 与原版血条布局对齐：心形条左缘 x = 屏宽/2 - 91；首行 y = 屏高 - 39（原版 leftHeight 初值）
        int heartsLeft = gui.guiWidth() / 2 - 91;
        int healthRowY = gui.guiHeight() - 39;
        int absorptionRowY = healthRowY - 10; // 原版吸收行在生命行上方一行

        // ① 伤害吸收（上一行，仅在存在且开关开启时显示）——顺序与原版一致
        if (showAbsorption && realAbsorption > 0.0f) {
            String txt = showMax
                    ? Component.translatable("hud.zifeng_s_custom_skill_tree.absorption_max",
                            fmt(realAbsorption), fmt(realAbsorption)).getString()
                    : Component.translatable("hud.zifeng_s_custom_skill_tree.absorption",
                            fmt(realAbsorption)).getString();
            drawRightAligned(gui, mc, txt, heartsLeft - GAP, absorptionRowY, 0xFFFFD700);
        }

        // ② 普通生命（下一行）
        if (showHealth) {
            String txt = showMax
                    ? Component.translatable("hud.zifeng_s_custom_skill_tree.health_max",
                            fmt(realHealth), fmt(realMax)).getString()
                    : Component.translatable("hud.zifeng_s_custom_skill_tree.health",
                            fmt(realHealth)).getString();
            drawRightAligned(gui, mc, txt, heartsLeft - GAP, healthRowY, 0xFFFF5555);
        }

        // ③ 护甲（护甲条那一行的左侧；护甲 > 20 才显示，与原版 10 个图标满甲对齐）
        if (showArmor && HealthBarHelper.shouldShowArmorNumber(realArmor)) {
            int armorRowY = HealthBarHelper.armorRowY(gui.guiHeight());
            if (armorRowY != Integer.MIN_VALUE) {
                String txt = Component.translatable("hud.zifeng_s_custom_skill_tree.armor",
                        realArmor).getString();
                drawRightAligned(gui, mc, txt, heartsLeft - GAP, armorRowY, 0xFF5FC8E8);
            }
        }
    }

    /** 1 位小数格式化（真实血量可能带小数，如 19.5） */
    private static String fmt(float v) {
        return String.format("%.1f", v);
    }

    /**
     * 右对齐绘制（文字右端对齐到 rightEdge）。
     * <p>⚠️ 2026-09-11：<b>不再铺黑色背景</b>（用户反馈底色难看）—— 改用带阴影的文字保证可读性。
     * 数字层级仍在其他血条模组的层之上（见 {@code registerGuiLayers}），一般不会被覆盖。
     */
    private static void drawRightAligned(GuiGraphics gui, Minecraft mc, String text, int rightEdge, int y, int color) {
        int w = mc.font.width(text);
        int x = rightEdge - w;
        gui.drawString(mc.font, text, x, y, color, true);
    }
}
