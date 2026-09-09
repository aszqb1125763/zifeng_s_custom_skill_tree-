package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 磁铁屏蔽区 · 木棍左键选区（2026-09-07 客户端 / 2026-09-08 迁入木棍工具层 RANGE 模块）：
 * <ul>
 *   <li>条件：手持原版木棍 + 工具总开关开 + 工具模式=RANGE + 磁铁已学（仅已学判定，磁铁开关不影响配置）</li>
 *   <li>钩子：InputEvent.InteractionKeyMappingTriggered（isAttack）——NeoForge patch
 *       Minecraft.startAttack / 持续挖掘 处 fire，cancel 后不破坏方块、不攻击实体</li>
 *   <li>左键（按下边沿，按玩家 tick 防抖）：射线命中点 = 角点——无第一角 → 记第一角（预览开始）；
 *       已有第一角 → 第二角成区（发 C2S ADD），清预览</li>
 *   <li>潜行 + 左键：发 C2S REMOVE（服务端按视线射线删命中区），清预览</li>
 *   <li>角点 = 命中方块取格；对空/远点取视线 128 格尽头（角点可为空气）</li>
 * </ul>
 */
public final class MagnetExclusionInputHandler {
    private MagnetExclusionInputHandler() {
    }

    /** 上次处理的世界时刻（按住持续 fire 防抖；2026-09-09 弃用 tickCount——死亡重生归零会错乱） */
    private static long lastProcessedTime = -1;

    /** 成区冷却截止世界时刻（2026-09-09：原 tickCount 死亡重生后新身体从 0 重计 →
     *  旧冷却数字永久拦截输入 = "死亡后木棍工具概率失效"bug 根因；改用 ClientSession 世界时钟根治） */
    private static long blockUntilGameTime = -1;

    /** 会话重置（ClientSession 在进服/死亡重生/换维度/出服时调用）：丢弃旧身体遗留的临时输入状态 */
    static void resetSession() {
        blockUntilGameTime = -1;
        lastProcessedTime = -1;
        MagnetExclusionClientState.setFirstCorner(null);
    }

    /**
     * Alt 是否按住（2026-09-08：物理键直读——Screen.hasAltDown 依赖键盘事件修饰键，
     * Alt 常被系统/输入法吞掉导致检测不到；GLFW 直读左右 Alt 最可靠）。
     * 渲染器也读它：按住 Alt 时预览框显示眼前 1 格角点。
     */
    static boolean isAltDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long win = mc.getWindow().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }

    /** 未完成选区的第一角自动清除：脱手木棍 / 工具关 / 切走 RANGE 模块 / 磁铁未学 时不再显示旧预览 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (MagnetExclusionClientState.getFirstCorner() == null) {
            return;
        }
        boolean stillValid = mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)
                && isRangeModuleActiveClient();
        if (!stillValid) {
            MagnetExclusionClientState.setFirstCorner(null);
        }
        // ===== Alt+右键 选角（2026-09-08）：右键按下边沿由 keyUse.consumeClick 检测（最可靠）。
        //     按住 Alt 时角点 = 视线前方 1 格那格（用户定稿）；普通右键不抢；潜行右键不抢。
        if (canHandle() && !mc.player.isShiftKeyDown() && isAltDown()) {
            while (mc.options.keyUse.consumeClick()) {
                if (edgeFire()) {
                    clickCornerAt(mc, cornerInFront(mc));
                }
            }
        }
    }

    /** RANGE 模块激活（客户端）：工具开 + 模式=RANGE + 磁铁已学 */
    static boolean isRangeModuleActiveClient() {
        return ModKeyBindingEvents.isStickToolOnClient()
                && ModKeyBindingEvents.getStickToolModeClient() == org.zifeng.skilltree.skill.Skills.STICK_MODE_RANGE
                && ModKeyBindingEvents.isMagnetLearnedClientOnly();
    }

    /**
     * 取选区角点（2026-09-08 定稿版）：
     * <ul>
     *   <li>射线长度 20 格（用户确认："20 格视线内没有方块 → 选视线最近的空气"）</li>
     *   <li>20 格内命中方块 → 取该方块格（原逻辑）</li>
     *   <li>20 格内全是空气（对空/隔空）→ 取视线 20 格尽头那一格【空气】作为角点
     *       （方便在空中框选悬空区域，空气本身就是角点——不再吸附远处方块）</li>
     * </ul>
     * 渲染预览与落点共用本方法，保证所见即所得。
     */
    public static net.minecraft.core.BlockPos pickCorner() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.HitResult hit = mc.player.pick(20.0D, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            return bhr.getBlockPos().immutable();
        }
        // 20 格内无方块：视线 20 格尽头那格空气即角点
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        return net.minecraft.core.BlockPos.containing(eye.add(look.scale(20.0D)));
    }

    /** 左键（2026-09-08 定稿）：第一角/第二角成区；潜行+左键=删除区 */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        if (!canHandle()) {
            return;
        }
        if (!edgeFire()) {
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

    /**
     * Alt+右键（2026-09-08 定稿）：空中选区专用角点——
     * 视线 20 格内无方块 → 选 20 格尽头那格空气（悬空框区/隔空补角）；
     * 命中方块时同样按方块格取。与原版右键互不冲突（需按住 Alt）。
     */
    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        if (!isAltDown()) {
            return; // 必须物理按住 Alt（普通右键不抢）
        }
        if (!canHandle()) {
            return;
        }
        if (!edgeFire()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        clickCornerAt(mc, cornerInFront(mc));
    }

    /** Alt+右键角点：玩家视线前方 1 格的那格（用户定稿：眼前的空气）；渲染器预览也用 */
    static net.minecraft.core.BlockPos cornerInFront(Minecraft mc) {
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        return net.minecraft.core.BlockPos.containing(eye.add(look.scale(1.0D)));
    }

    /** 条件：手持木棍 + 工具开 + RANGE 模块（磁铁已学） + 无 GUI + 不在成区冷却期 */
    private static boolean canHandle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return false;
        }
        // 成区冷却：刚成区后 2 tick 内不响应任何点击（防两个选区贴脸重叠；世界时钟不随身体归零）
        if (ClientSession.now() < blockUntilGameTime) {
            return false;
        }
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)) {
            return false;
        }
        return isRangeModuleActiveClient();
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

    /** 选角（左键用）：角点由 pickCorner 决定 */
    private static void clickCorner(Minecraft mc) {
        clickCornerAt(mc, pickCorner());
    }

    /** 选角公共逻辑：第一角/第二角（左键与 Alt+右键共用同一块选区） */
    private static void clickCornerAt(Minecraft mc, BlockPos pos) {
        String dim = mc.level.dimension().location().toString();
        BlockPos first = MagnetExclusionClientState.getFirstCorner();
        if (first == null) {
            // 第一角：本地预览开始
            MagnetExclusionClientState.setFirstCorner(pos);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.0F);
        } else {
            // 本地校验：单边 ≤64 格、不与已有区重复（角点归一化后比对）
            int minX = Math.min(first.getX(), pos.getX()), maxX = Math.max(first.getX(), pos.getX());
            int minY = Math.min(first.getY(), pos.getY()), maxY = Math.max(first.getY(), pos.getY());
            int minZ = Math.min(first.getZ(), pos.getZ()), maxZ = Math.max(first.getZ(), pos.getZ());
            if (maxX - minX > 64 || maxY - minY > 64 || maxZ - minZ > 64) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.magnet_exclusion_too_large"), true);
                return; // 保留第一角：用户重新点第二角
            }
            boolean dup = false;
            for (org.zifeng.skilltree.data.MagnetExclusionZone z
                    : MagnetExclusionClientState.getZones()) {
                if (z.dim().equals(dim) && z.minX() == minX && z.minY() == minY && z.minZ() == minZ
                        && z.maxX() == maxX && z.maxY() == maxY && z.maxZ() == maxZ) {
                    dup = true;
                    break;
                }
            }
            // 每块独立两角：成区后清空第一角——下一次点击作为新一块第一角。
            if (dup) {
                MagnetExclusionClientState.setFirstCorner(null);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.magnet_exclusion_duplicate"), true);
                return;
            }
            // 第二角：成区发服务端（发完清空；进入 2 tick 成区冷却，防快速连点下一区贴脸）
            MagnetExclusionClientState.setFirstCorner(null);
            blockUntilGameTime = ClientSession.now() + 2;
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.MagnetExclusionC2SPacket(0, dim,
                    first.getX(), first.getY(), first.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()));
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 1.0F);
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.magnet_exclusion_added"), true);
        }
    }

    /** 潜行+左键：删除视线命中的屏蔽区 */
    private static void removeZone(Minecraft mc) {
        MagnetExclusionClientState.setFirstCorner(null);
        String dim = mc.level.dimension().location().toString();
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        if (MagnetExclusionRenderer.isRayHittingZone(eye, look)) {
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.MagnetExclusionC2SPacket(1, dim,
                    0, 0, 0, 0, 0, 0));
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.magnet_exclusion_removed"), true);
        } else {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.magnet_exclusion_miss"), true);
        }
    }
}
