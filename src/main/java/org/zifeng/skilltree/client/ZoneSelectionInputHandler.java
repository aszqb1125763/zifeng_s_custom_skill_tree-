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
    /**
     * 大选区温馨提醒阀值（格数）：超过则在成区时提示"可能卡顿"，由玩家自行决定是否使用。
     * <p>131072 ≈ 50³ / 13 万格（2026-09-12 用户指定）。
     * <p>实际处理不会单帧卡死（ZoneSkillEvents 分 tick 处理），但总量大自然要等更久。
     */
    private static final long LARGE_ZONE_WARN_VOLUME = 131072L;

    /** 会话重置（ClientSession 在进服/死亡重生/换维度/出服时调用）：丢弃旧身体遗留的临时输入状态 */
    static void resetSession() {
        blockUntilGameTime = -1;
        lastProcessedTime = -1;
        ZoneExclusionClientState.setFirstCorner(null);
        resetPendingScroll(); // 待发的滚轮调整一并丢弃（防残留到新会话）
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
        // 滚轮累积的调整量：每 tick 最多发一个包（不受下面 firstCorner 早退影响）
        flushPendingScroll();
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

    // ============ 滚轮微调选区（2026-09-12 1.4.1）============

    /** 滚轮微调步进（普通 / Shift 加速） */
    private static final int SCROLL_STEP = 1;
    private static final int SCROLL_STEP_SHIFT = 10;
    /**
     * 待发送的累积滚轮步进（带符号；0 = 无待发）。
     * <p><b>为什么要累积：</b>原版 {@code MouseHandler.onScroll} 里
     * {@code accumulatedScroll} 会把滚动量累加到整格才触发事件 → 快速滚动一 tick 内可产生
     * <b>多个</b>事件。若每个事件都立即发包，服务端会连续回发整份技能数据（大包）；
     * 若像旧版那样只处理第一个、后续直接 return，<b>那些事件就没被取消</b>
     * → 穿透到 {@code Inventory.swapPaint} → <b>快捷栏被切换</b>（用户实测反馈：滚太快仍会切）。
     * <p>现在：<b>每个事件都无条件取消</b>（不切快捷栏）+ 累积步进，
     * 由 {@link #flushPendingScroll} 每 tick 最多发一个包（调整量不丢）。
     */
    private static int pendingScroll = 0;
    /** 累积期间最后一次瞄准的区/面（多次滚动取最后一次；视线基本不会一 tick 内大幅变化） */
    private static ScrollHit pendingScrollHit = null;
    /**
     * 累积对应的包 action（30-33，= 模式 + 30）。
     * <p>⚠️ 必须在【滚动时】就固定：若在 flush 时再算 {@code actionOfMode()}，
     * 玩家在这 1 tick 内切换了木棍模式 → 会拿新技能去改旧技能的选区（改错区）。
     */
    private static int pendingScrollAction = -1;

    /**
     * 滚轮微调选区（2026-09-12 1.4.1）：
     * <b>对准已框选的区域 → 滚轮调整「正对玩家的那个面」</b>（近面）。
     * <ul>
     *   <li>上滚 = 该面朝【外】移动（区域变大）；下滚 = 向内缩；Shift 步进 10 格</li>
     *   <li>目标 = 当前木棍模式对应技能的区（防护多块时取射线命中最近的一块）</li>
     *   <li>未对准任何区时不拦截 → 保留原版快捷栏/旁观者滚轮</li>
     *   <li>实际计算与合法性校验在服务端（客户端只陈述"调整哪个面、多少格"）</li>
     * </ul>
     * <p>⚠️ 平台差异：1.20.1 用 {@code getScrollDelta()}；1.21.1 拆成 X/Y 分量，取 {@code getScrollDeltaY()}。
     */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!canHandle()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ScrollHit hit = pickZoneForScroll(mc);
        if (hit == null) {
            return; // 没对准任何区：不拦截（保留原版行为）
        }
        // ⚠️ 关键：无条件取消（只要对准了区）。绝不能因为"同一 tick 已处理过"就跳过取消，
        //    否则事件会穿透到 MouseHandler 末尾的 Inventory.swapPaint → 快捷栏乱切。
        event.setCanceled(true);
        int step = mc.player.isShiftKeyDown() ? SCROLL_STEP_SHIFT : SCROLL_STEP;
        pendingScroll += (delta > 0 ? step : -step); // 正 = 该面向外扩
        pendingScrollHit = hit;
        pendingScrollAction = actionOfMode() + 30; // ⚠️ 滚动时固定 action（防 flush 时模式已切）
    }

    /** 每 tick 发包一次（累积步进）；由 {@link #onClientTick} 调用 */
    private static void flushPendingScroll() {
        if (pendingScroll == 0 || pendingScrollHit == null || pendingScrollAction < 0) {
            return;
        }
        int steps = pendingScroll;
        ScrollHit hit = pendingScrollHit;
        int action = pendingScrollAction;
        pendingScroll = 0;
        pendingScrollHit = null;
        pendingScrollAction = -1;
        org.zifeng.skilltree.network.ModNetwork.sendToServer(
                new org.zifeng.skilltree.network.ZoneC2SPacket(
                        action, hit.zone().dim(),
                        hit.anchor().getX(), hit.anchor().getY(), hit.anchor().getZ(),
                        hit.face().ordinal(), steps, 0));
    }

    /** 会话重置时丢弃待发滚轮（避免残留到下一会话） */
    private static void resetPendingScroll() {
        pendingScroll = 0;
        pendingScrollHit = null;
        pendingScrollAction = -1;
    }

    /** 滚轮命中结果：区域 + 命中面 + 区内锚点（防护多块时服务端靠锤点定位） */
    private record ScrollHit(OperZone zone, net.minecraft.core.Direction face, BlockPos anchor) {
    }

    /** 射线找当前模式下最近的区（返回区 + 正对玩家的近面） */
    private static ScrollHit pickZoneForScroll(Minecraft mc) {
        String skill = zoneSkillOfCurrentMode();
        if (skill == null) {
            return null;
        }
        String dim = mc.level.dimension().location().toString();
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
            return null;
        }
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(mc.player.getLookAngle().scale(200.0D));
        ScrollHit best = null;
        double bestDist = Double.MAX_VALUE;
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1);
            net.minecraft.core.Direction face = rayFace(box, eye, end);
            if (face == null) {
                continue;
            }
            double d = box.getCenter().distanceToSqr(eye);
            if (d < bestDist) {
                bestDist = d;
                best = new ScrollHit(zone, face, BlockPos.containing(box.getCenter()));
            }
        }
        return best;
    }

    /**
     * 求射线与矩形盒相交时「正对射线起点」的面（近面）。
     * <ul>
     *   <li>起点在盒外 → 返回入射面（标准 slab 法；未命中返回 null）</li>
     *   <li>起点在盒内 → 返回视线穿出的那个面（此时"近面"退化为视线一侧）</li>
     * </ul>
     * <p>⚠️ 不用 {@code AABB.clip(Iterable, Vec3, Vec3, BlockPos)}：源码里它会先把每个盒
     * <b>平移指定偏移</b>（{@code aabb.move(pos)}，偏移不是盒位置而是位移量）→ 极易误用；
     * 它也不处理起点在盒内（此时返回 null）。自写算法对两种情形都确定。
     */
    private static net.minecraft.core.Direction rayFace(net.minecraft.world.phys.AABB box,
                                                        net.minecraft.world.phys.Vec3 from,
                                                        net.minecraft.world.phys.Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        boolean inside = from.x > box.minX && from.x < box.maxX
                && from.y > box.minY && from.y < box.maxY
                && from.z > box.minZ && from.z < box.maxZ;
        if (inside) {
            // 起点在盒内：取三轴出口中最近的那个面
            double best = Double.MAX_VALUE;
            net.minecraft.core.Direction face = null;
            if (dx > 1.0E-7) {
                best = (box.maxX - from.x) / dx;
                face = net.minecraft.core.Direction.EAST;
            } else if (dx < -1.0E-7) {
                best = (box.minX - from.x) / dx;
                face = net.minecraft.core.Direction.WEST;
            }
            if (dy > 1.0E-7) {
                double t = (box.maxY - from.y) / dy;
                if (t < best) {
                    best = t;
                    face = net.minecraft.core.Direction.UP;
                }
            } else if (dy < -1.0E-7) {
                double t = (box.minY - from.y) / dy;
                if (t < best) {
                    best = t;
                    face = net.minecraft.core.Direction.DOWN;
                }
            }
            if (dz > 1.0E-7) {
                double t = (box.maxZ - from.z) / dz;
                if (t < best) {
                    face = net.minecraft.core.Direction.SOUTH;
                }
            } else if (dz < -1.0E-7) {
                double t = (box.minZ - from.z) / dz;
                if (t < best) {
                    face = net.minecraft.core.Direction.NORTH;
                }
            }
            return face;
        }
        // 起点在盒外：标准 slab（tMin 所在轴 = 入射面）
        double tMin = 0.0D, tMax = 1.0D;
        net.minecraft.core.Direction face = null;
        if (Math.abs(dx) < 1.0E-7) {
            if (from.x < box.minX || from.x > box.maxX) {
                return null;
            }
        } else {
            double t1 = (box.minX - from.x) / dx, t2 = (box.maxX - from.x) / dx;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dx > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
            }
            tMax = Math.min(tMax, tFar);
        }
        if (Math.abs(dy) < 1.0E-7) {
            if (from.y < box.minY || from.y > box.maxY) {
                return null;
            }
        } else {
            double t1 = (box.minY - from.y) / dy, t2 = (box.maxY - from.y) / dy;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dy > 0 ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP;
            }
            tMax = Math.min(tMax, tFar);
        }
        if (Math.abs(dz) < 1.0E-7) {
            if (from.z < box.minZ || from.z > box.maxZ) {
                return null;
            }
        } else {
            double t1 = (box.minZ - from.z) / dz, t2 = (box.maxZ - from.z) / dz;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dz > 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
            }
            tMax = Math.min(tMax, tFar);
        }
        return tMin > tMax ? null : face;
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
        // 本地校验单边 ≤ OperZone.MAX_SIDE 格（超限保留第一角重选，提示同磁铁）
        int minX = Math.min(first.getX(), pos.getX()), maxX = Math.max(first.getX(), pos.getX());
        int minY = Math.min(first.getY(), pos.getY()), maxY = Math.max(first.getY(), pos.getY());
        int minZ = Math.min(first.getZ(), pos.getZ()), maxZ = Math.max(first.getZ(), pos.getZ());
        if (maxX - minX > OperZone.MAX_SIDE || maxY - minY > OperZone.MAX_SIDE || maxZ - minZ > OperZone.MAX_SIDE) {
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_too_large", String.valueOf(OperZone.MAX_SIDE)), true);
            return;
        }
        // 大选区温馨提醒（2026-09-12）：格数超阀值→提示可能卡顿，由玩家自行决定是否使用
        // （⚠️ 参数必须传 String/原语——传 Long 等对象会导致组件网络编码失败踢人）
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > LARGE_ZONE_WARN_VOLUME) {
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_large_warn", String.valueOf(volume)), true);
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
