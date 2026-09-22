package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.data.MagnetExclusionZone;
import org.zifeng.skilltree.network.MagnetExclusionC2SPacket;

/**
 * 磁铁屏蔽区 · 木棍左键选区（2026-09-07 客户端 / 2026-09-08 迁入木棍工具层 RANGE 模块）：
 * <ul>
 *   <li>条件：手持原版木棍 + 工具总开关开 + 工具模式=RANGE + 磁铁已学（仅已学判定，磁铁开关不影响配置）</li>
 *   <li>钩子：InputEvent.InteractionKeyMappingTriggered（isAttack）——Forge patch
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
        attackHeld = false;
        useHeld = false;
        MagnetExclusionClientState.setFirstCorner(null);
        resetPendingScroll(); // 待发的滚轮调整一并丢弃（防残留到新会话）
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // ★ 2026-09-22：滚轮累积的调整量每 tick 最多发一个包
        //   （必须放在 firstCorner 早退【之前】—— 滚轮微调与「是否在选第一角」无关）
        flushPendingScroll();
        // ★ 2026-09-22：鼠标按键抬起 → 复位「按住」标志。
        //   必须在下面 firstCorner 早退【之前】—— 否则松手后再按就不认新点击了。
        if (!mc.options.keyAttack.isDown()) {
            attackHeld = false;
        }
        if (!mc.options.keyUse.isDown()) {
            useHeld = false;
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

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return; // 只拦左键攻击（不影响右键使用/中键选块）
        }
        if (!canHandle()) {
            return;
        }
        // ★ 2026-09-22：激活期间【无条件取消】—— 长按左键时原版 continueAttack
        //   每 tick 都触发本事件；旧版"未通过去重就提前 return"会让这些事件不被取消，
        //   结果就是"一边选角一边把方块挖了"。
        event.setCanceled(true);
        event.setSwingHand(true);
        if (!edgeFire()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isShiftKeyDown()) {
            // 潜行 + 左键：删除视线命中的屏蔽区
            removeZone(mc);
        } else {
            // 左键：第一角/第二角成区
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
        if (!edgeFireUse()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isShiftKeyDown()) {
            return; // 潜行右键留给其它语义（不抢）
        }
        // 拦截原版右键（不放置/不使用）；保留挥动
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

    /** 上一 tick 末左键是否仍按住（★ 2026-09-22：用于「上升沿」判定） */
    private static boolean attackHeld = false;
    /** 上一 tick 末右键是否仍按住 */
    private static boolean useHeld = false;

    /**
     * 左键边沿判定：<b>同 tick 去重 + 按住期间的重复触发去重</b>（★ 2026-09-22 重写）。
     * <p>逻辑与说明见 {@link #edgeFireInternal}。
     */
    private static boolean edgeFire() {
        return edgeFireInternal(true);
    }

    /**
     * 右键边沿判定（★ 2026-09-22 新增）。
     * <p>原版 {@code startUseItem()} 在<b>按住</b>右键时每 {@code rightClickDelay}（4）tick
     * 触发一次 {@code onClickInput}，因此 Alt+右键 选角同样需要上升沿判定。
     */
    private static boolean edgeFireUse() {
        return edgeFireInternal(false);
    }

    /**
     * 边沿判定内部实现（左右键共用）。
     *
     * <h2>为什么旧的 edgeFire() 不够（本次 bug 的根因）</h2>
     * 原版 {@code Minecraft.handleKeybinds()} 对左键会<b>两次</b>触发
     * {@code InteractionKeyMappingTriggered}（已核对 1.20.1 字节码与 1.21.1 源码）：
     * <ol>
     *   <li>{@code startAttack()} —— 每次点击触发 <b>1 次</b>（{@code consumeClick()} 边沿）</li>
     *   <li>{@code continueAttack(isDown)} —— <b>按住期间每 tick 都触发 1 次</b>，
     *       其前置条件为「{@code hitResult} 是方块且该格非空气」
     *       → 这正是用户反馈「<b>近距离</b>按一下容易触发两次选区」的原因</li>
     * </ol>
     * 旧实现只做「同一游戏时刻（tick）只处理一次」去重，<b>挡不住跨 tick 的持续触发</b>
     * → 玩家按下左键选第一个角后，之后每一 tick 都会再选一次角，
     * 选区被"自动"闭合（用户实测：「比较容易触发时玩家长按左键的时候」）。
     *
     * <p>现在额外要求<b>上升沿</b>：该键在上一 tick 末若已按住 → 直接忽略。
     * 标志位由 {@link #onClientTick} 在按键抬起时复位（不依赖 tick 回调与
     * {@code handleKeybinds} 的先后顺序）。
     */
    private static boolean edgeFireInternal(boolean attack) {
        long now = ClientSession.now();
        if (now == lastProcessedTime) {
            return false; // 同一 tick 内已有事件被接受
        }
        Minecraft mc = Minecraft.getInstance();
        boolean down = attack ? mc.options.keyAttack.isDown() : mc.options.keyUse.isDown();
        boolean held = attack ? attackHeld : useHeld;
        if (down && held) {
            // 按住期间的重复触发（continueAttack 每 tick / startUseItem 每 4 tick）
            // ⚠️ 这里【不】更新 lastProcessedTime —— 让同 tick 的后续事件继续被第一道判断挡住
            return false;
        }
        if (attack) {
            attackHeld = down;
        } else {
            useHeld = down;
        }
        lastProcessedTime = now;
        return true;
    }

    // ============ 滚轮微调屏蔽区（★ 2026-09-22 新增：与区块技能同步）============

    /**
     * <b>背景</b>：滚轮微调原本只做在机械共鸣区块技能上，磁铁屏蔽区没有 ——
     * 用户反馈「区块拆解的选区各种滚轮操作，没有同步到各种选区操作，比如磁铁这些」。
     * 现在两种区共用同一套行为（面语义、步进、累积发包、快捷栏保护）。
     *
     * <p>⚠️ 与 {@link ZoneSelectionInputHandler} 保持<b>逐项对齐</b>：
     * 改动其中一处必须同步另一处，否则两处手感会不一致。
     */
    private static final int SCROLL_STEP = 1;
    private static final int SCROLL_STEP_SHIFT = 10;

    /**
     * 待发送的累积滚轮步进（带符号；0 = 无待发）。
     *
     * <p><b>为什么要累积：</b>原版 {@code MouseHandler.onScroll} 里
     * {@code accumulatedScroll} 会把滚动量累加到整格才触发事件 → 快速滚动一 tick 内可产生
     * <b>多个</b>事件。若每个事件都立即发包，服务端会连续回发整份技能数据/全服广播（大包）；
     * 若只处理第一个、后续直接 return，那些事件<b>就没被取消</b>
     * → 穿透到 {@code Inventory.swapPaint} → <b>快捷栏被切换</b>。
     * <p>所以：<b>每个事件都无条件取消</b>（不切快捷栏）+ 累积步进，
     * 由 {@link #flushPendingScroll} 每 tick 最多发一个包（调整量不丢）。
     */
    private static int pendingScroll = 0;
    /** 累积期间最后一次瞄准的区 + 面（取最后一次；视线基本不会一 tick 内大幅变化） */
    private static ScrollHit pendingScrollHit = null;

    /** 滚轮命中结果：屏蔽区 + 正对玩家的近面 */
    private record ScrollHit(org.zifeng.skilltree.data.MagnetExclusionZone zone,
                             net.minecraft.core.Direction face) {
    }

    /**
     * 滚轮微调屏蔽区：<b>对准已框选的屏蔽区 → 滚轮调整「正对玩家的那个面」</b>（近面）。
     * <ul>
     *   <li>上滚 = 该面朝【外】移动（区域变大）；下滚 = 向内缩；Shift 步进 10 格</li>
     *   <li>未对准任何区时不拦截 → 保留原版快捷栏滚轮</li>
     *   <li>实际计算与合法性校验在服务端（客户端只陈述"调整哪个区、哪个面、多少格"）</li>
     * </ul>
     * <p>⚠️ 平台差异：1.20.1 用 {@code getScrollDelta()}；1.21.1 拆成 X/Y 分量，取 {@code getScrollDeltaY()}。
     */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!canHandle()) {
            return;
        }
        double delta = event.getScrollDelta();
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
    }

    /** 每 tick 发包一次（累积步进）；由 {@link #onClientTick} 调用 */
    private static void flushPendingScroll() {
        if (pendingScroll == 0 || pendingScrollHit == null) {
            return;
        }
        int steps = pendingScroll;
        ScrollHit hit = pendingScrollHit;
        pendingScroll = 0;
        pendingScrollHit = null;
        // type=2 ADJUST：ax/ay/az = 区的最小角（无歧义标识），
        //                bx = 面序号，by = 带符号步进
        org.zifeng.skilltree.network.ModNetwork.sendToServer(
                new org.zifeng.skilltree.network.MagnetExclusionC2SPacket(
                        2, hit.zone().dim(),
                        hit.zone().minX(), hit.zone().minY(), hit.zone().minZ(),
                        hit.face().ordinal(), steps, 0));
    }

    /** 会话重置时丢弃待发滚轮（避免残留到下一会话） */
    private static void resetPendingScroll() {
        pendingScroll = 0;
        pendingScrollHit = null;
    }

    /** 射线找最近的屏蔽区（返回区 + 正对玩家的近面）；几何计算与区块技能共用 {@link ZoneRayUtil} */
    private static ScrollHit pickZoneForScroll(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        String dim = mc.level.dimension().location().toString();
        java.util.List<org.zifeng.skilltree.data.MagnetExclusionZone> zones =
                MagnetExclusionClientState.getZones();
        if (zones.isEmpty()) {
            return null;
        }
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(mc.player.getLookAngle().scale(200.0D));
        ScrollHit best = null;
        double bestDist = Double.MAX_VALUE;
        for (org.zifeng.skilltree.data.MagnetExclusionZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = ZoneRayUtil.boxOf(
                    zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX(), zone.maxY(), zone.maxZ());
            net.minecraft.core.Direction face = ZoneRayUtil.rayFace(box, eye, end);
            if (face == null) {
                continue;
            }
            double d = box.getCenter().distanceToSqr(eye);
            if (d < bestDist) {
                bestDist = d;
                best = new ScrollHit(zone, face);
            }
        }
        return best;
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
            // 本地校验：单边 ≤ OperZone.MAX_SIDE 格、不与已有区重复（角点归一化后比对）
            int minX = Math.min(first.getX(), pos.getX()), maxX = Math.max(first.getX(), pos.getX());
            int minY = Math.min(first.getY(), pos.getY()), maxY = Math.max(first.getY(), pos.getY());
            int minZ = Math.min(first.getZ(), pos.getZ()), maxZ = Math.max(first.getZ(), pos.getZ());
            if (maxX - minX > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                    || maxY - minY > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                    || maxZ - minZ > org.zifeng.skilltree.data.OperZone.MAX_SIDE) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.magnet_exclusion_too_large",
                        String.valueOf(org.zifeng.skilltree.data.OperZone.MAX_SIDE)), true);
                return; // 保留第一角：用户重新点第二角
            }
            boolean dup = false;
            for (MagnetExclusionZone z : MagnetExclusionClientState.getZones()) {
                if (z.dim().equals(dim) && z.minX() == minX && z.minY() == minY && z.minZ() == minZ
                        && z.maxX() == maxX && z.maxY() == maxY && z.maxZ() == maxZ) {
                    dup = true;
                    break;
                }
            }
            // 每块独立两角：成区后清空第一角——下一次点击作为新一块第一角。
            if (dup) {
                MagnetExclusionClientState.setFirstCorner(null);
                mc.player.displayClientMessage(Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.magnet_exclusion_duplicate"), true);
                return;
            }
            // 第二角：成区发服务端（发完清空；进入 2 tick 成区冷却，防快速连点下一区贴脸）
            MagnetExclusionClientState.setFirstCorner(null);
            blockUntilGameTime = ClientSession.now() + 2;
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new MagnetExclusionC2SPacket(0, dim,
                    first.getX(), first.getY(), first.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()));
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 1.0F);
            mc.player.displayClientMessage(Component.translatable(
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
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new MagnetExclusionC2SPacket(1, dim,
                    0, 0, 0, 0, 0, 0));
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.magnet_exclusion_removed"), true);
        } else {
            mc.player.displayClientMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.magnet_exclusion_miss"), true);
        }
    }
}
