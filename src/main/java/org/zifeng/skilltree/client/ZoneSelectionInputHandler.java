package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·操作区 框选（2026-09-08 客户端，木棍工具模式 2-5）：
 * <ul>
 *   <li>条件：手持木棍 + 工具开 + 模式=放置/挖掘/攻击/防护 且对应技能已学（仅已学判定，技能开关不影响配置）</li>
 *   <li>左键（按下边沿）：第一角/第二角成区 → 发 ZoneC2SPacket(action 0-3 设对应技能区)，覆盖旧区</li>
 *   <li>潜行 + 左键：删除当前模式下已框选的操作区（射线命中该技能区任意格 → 发同技能清除包）</li>
 *   <li>Alt+右键：眼前 1 格空气角点（与磁铁选区交互完全一致）</li>
 * </ul>
 * 交互语义与 MagnetExclusionInputHandler 逐项对齐（每块独立两角 / 2 tick 成区冷却 / 角点 20 格规则）。
 */
public final class ZoneSelectionInputHandler {
    private ZoneSelectionInputHandler() {
    }

    private static long lastProcessedTime = -1;
    /** 成区冷却截止世界时刻（2026-09-09：原 tickCount 死亡重生归零 → 旧冷却永久拦截 = "木棍概率失效"根因；
     *  与磁铁选区同款 2 tick 防连点；改用 ClientSession 世界时钟根治） */
    private static long blockUntilGameTime = -1;

    /** 会话重置（ClientSession 在进服/死亡重生/换维度/出服时调用）：丢弃旧身体遗留的临时输入状态 */
    static void resetSession() {
        blockUntilGameTime = -1;
        lastProcessedTime = -1;
        ZoneExclusionClientState.setFirstCorner(null);
    }

    /** 当前工具模式下对应的机械共鸣技能（模式 2-5；BIND/RANGE 返回 null） */
    static String zoneSkillOfCurrentMode() {
        int mode = ModKeyBindingEvents.getStickToolModeClient();
        if (mode < Skills.STICK_MODE_ZONE_PLACE || mode > Skills.STICK_MODE_ZONE_PROTECT) {
            return null;
        }
        return Skills.skillForStickMode(mode);
    }

    /** 当前机械共鸣模式激活（客户端）：工具开 + 模式 2-5 + 对应技能已学 */
    static boolean isZoneModuleActiveClient() {
        String skill = zoneSkillOfCurrentMode();
        if (skill == null) {
            return false;
        }
        return ModKeyBindingEvents.isStickToolOnClient()
                && ModKeyBindingEvents.isStickModeUnlocked(ModKeyBindingEvents.getStickToolModeClient());
    }

    /** 模式 → ZoneC2SPacket 设置 action（0=放置 1=挖掘 2=攻击 3=防护） */
    private static int actionOfMode() {
        return ModKeyBindingEvents.getStickToolModeClient() - Skills.STICK_MODE_ZONE_PLACE;
    }

    /** 模式 → 操作区（已存，null=未框选） */
    static OperZone zoneOfCurrentMode() {
        String skill = zoneSkillOfCurrentMode();
        return skill == null ? null : ModKeyBindingEvents.getOperZoneClient(skill);
    }

    /** 清预览：脱手木棍 / 工具关 / 切走当前模式 时不再显示旧预览 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (ZoneExclusionClientState.getFirstCorner() == null) {
            return;
        }
        boolean stillValid = mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)
                && isZoneModuleActiveClient();
        if (!stillValid) {
            ZoneExclusionClientState.setFirstCorner(null);
        }
        // ===== Alt+右键 选角（与磁铁同款：按住 Alt 时角点=视线前方 1 格空气） =====
        if (canHandle() && !mc.player.isShiftKeyDown() && MagnetExclusionInputHandler.isAltDown()) {
            while (mc.options.keyUse.consumeClick()) {
                if (edgeFire()) {
                    clickCornerAt(mc, MagnetExclusionInputHandler.cornerInFront(mc));
                }
            }
        }
    }

    /** 左键：第一角/第二角成区；潜行+左键=删除当前技能操作区 */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !canHandle() || !edgeFire()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isShiftKeyDown()) {
            removeZone(mc);
        } else {
            clickCorner(mc);
        }
    }

    /** Alt+右键：视线前方 1 格空气角点（与磁铁同款；需物理按住 Alt） */
    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !MagnetExclusionInputHandler.isAltDown()) {
            return;
        }
        if (!canHandle() || !edgeFire()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        clickCornerAt(mc, MagnetExclusionInputHandler.cornerInFront(mc));
    }

    private static boolean canHandle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return false;
        }
        if (ClientSession.now() < blockUntilGameTime) {
            return false;
        }
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)) {
            return false;
        }
        return isZoneModuleActiveClient();
    }

    /** 边沿防抖：同一世界时刻只处理一次（换服由 ClientSession.resetSession 重置兜底） */
    private static boolean edgeFire() {
        long now = ClientSession.now();
        if (now == lastProcessedTime) {
            return false;
        }
        lastProcessedTime = now;
        return true;
    }

    private static void clickCorner(Minecraft mc) {
        clickCornerAt(mc, MagnetExclusionInputHandler.pickCorner());
    }

    /** 选角公共逻辑（对齐磁铁：第一角/第二角；成区即覆盖旧操作区，无需去重——单区覆盖语义） */
    private static void clickCornerAt(Minecraft mc, BlockPos pos) {
        String dim = mc.level.dimension().location().toString();
        BlockPos first = ZoneExclusionClientState.getFirstCorner();
        if (first == null) {
            ZoneExclusionClientState.setFirstCorner(pos);
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.0F);
            return;
        }
        // 本地校验单边 ≤64 格（超限保留第一角重选，提示同磁铁）
        int minX = Math.min(first.getX(), pos.getX()), maxX = Math.max(first.getX(), pos.getX());
        int minY = Math.min(first.getY(), pos.getY()), maxY = Math.max(first.getY(), pos.getY());
        int minZ = Math.min(first.getZ(), pos.getZ()), maxZ = Math.max(first.getZ(), pos.getZ());
        if (maxX - minX > 64 || maxY - minY > 64 || maxZ - minZ > 64) {
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_too_large"), true);
            return;
        }
        ZoneExclusionClientState.setFirstCorner(null);
        blockUntilGameTime = ClientSession.now() + 2;
        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ZoneC2SPacket(actionOfMode(), dim,
                minX, minY, minZ, maxX, maxY, maxZ));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 1.0F);
        mc.player.displayClientMessage(Component.translatable(
                "chat.zifeng_s_custom_skill_tree.zone_set"), true);
    }

    /** 潜行+左键：删除当前模式下已框选的操作区（射线命中 → 发同技能清除包） */
    private static void removeZone(Minecraft mc) {
        ZoneExclusionClientState.setFirstCorner(null);
        String skill = zoneSkillOfCurrentMode();
        String dim = mc.level.dimension().location().toString();
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        // 当前技能可展示的全部区：防护 = 多块列表；其余 = 单块 map
        java.util.List<OperZone> zones = new java.util.ArrayList<>();
        if (Skills.MACHINE_ZONE_PROTECT.equals(skill)) {
            zones.addAll(ModKeyBindingEvents.getProtectZonesClient());
        } else {
            OperZone single = ModKeyBindingEvents.getOperZoneClient(skill);
            if (single != null) {
                zones.add(single);
            }
        }
        if (zones.isEmpty()) {
            return; // 本就未框选：无反应
        }
        // 找射线命中的那块（近处优先）
        OperZone hitZone = null;
        double bestDist = Double.MAX_VALUE;
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1);
            var hit = box.clip(eye, eye.add(look.scale(200))).orElse(null);
            if (hit != null) {
                double d = eye.distanceToSqr(hit);
                if (d < bestDist) {
                    bestDist = d;
                    hitZone = zone;
                }
            }
        }
        if (hitZone == null) {
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_miss"), true);
            return;
        }
        // 清除：防护（action 23）带命中区一角坐标删单块；其余单块技能（action 20-22）直接清
        int action = actionOfMode() + 20;
        if (Skills.MACHINE_ZONE_PROTECT.equals(skill)) {
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ZoneC2SPacket(23, dim,
                    hitZone.minX(), hitZone.minY(), hitZone.minZ(), 0, 0, 0));
        } else {
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ZoneC2SPacket(
                    action, dim, 0, 0, 0, 0, 0, 0));
        }
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.0F);
        mc.player.displayClientMessage(Component.translatable(
                "chat.zifeng_s_custom_skill_tree.zone_removed"), true);
    }
}
