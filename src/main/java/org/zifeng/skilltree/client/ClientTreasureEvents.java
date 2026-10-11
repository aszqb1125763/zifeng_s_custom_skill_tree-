package org.zifeng.skilltree.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 寻宝大师（TREASURE_HUNTER，2026-08-27）：64 格内战利品容器与考古刷扫点白色发光轮廓。
 * <p>客户端渲染：不创建实体、不碰任何模组数据 → 兼容性极好（所有实现 RandomizableContainer
 * 的容器：原版箱子/桶/潜影盒/漏斗 + 任意模组战利品容器；以及考古刷扫方块）。
 * <p>实现：
 * <ul>
 *   <li>每 1 秒扫描一次玩家 64 格内的目标方块实体，缓存 BlockPos（避免每帧遍历）</li>
 *   <li>{@link RenderLevelStageEvent.AFTER_BLOCK_ENTITIES} 画白色半透明线框（类似原版发光轮廓）</li>
 *   <li>技能状态用客户端本地缓存（服务端 S2CPacket 校准），多人下仅自己可见</li>
 * </ul>
 */
public class ClientTreasureEvents {

    private static final int SCAN_RADIUS_SQ = 64 * 64;        // 水平半径 64 格
    private static final int SCAN_Y_RADIUS_SQ = 128 * 128;    // 垂直半径 128 格（y 轴范围翻倍，2026-08-27）
    // 金色高亮（2026-08-27）：白线太细不显眼 → 金色双线框（外暗金粗线 + 内亮金），远看是粗金框
    private static final float OUTER_R = 1.0F, OUTER_G = 0.78F, OUTER_B = 0.15F;   // 暗金（外层）
    private static final float INNER_R = 1.0F, INNER_G = 0.92F, INNER_B = 0.35F;   // 亮金（内层）
    private static final float OUTLINE_ALPHA = 1.0F; // 纯色不透明（alpha=1 → 混合时 dst 贡献 0 → 颜色不受背后方块影响，2026-08-27 同步 1.20.1 修复）
    // 刷怪笼高亮（2026-08-27）：品红色双线框（与金色容器区分，刷怪笼火焰粒子同色系）
    private static final float SPAWNER_OUTER_R = 0.75F, SPAWNER_OUTER_G = 0.1F, SPAWNER_OUTER_B = 0.9F;  // 深品红（外层）
    private static final float SPAWNER_INNER_R = 1.0F, SPAWNER_INNER_G = 0.5F, SPAWNER_INNER_B = 1.0F;  // 亮品红（内层）
    // 考古方块高亮（2026-08-27）：青色双线框（与金色容器/品红刷怪笼区分，考古主题：可疑沙砾/沙子）
    private static final float ARCH_OUTER_R = 0.1F, ARCH_OUTER_G = 0.85F, ARCH_OUTER_B = 0.85F;   // 深青（外层）
    private static final float ARCH_INNER_R = 0.6F, ARCH_INNER_G = 1.0F, ARCH_INNER_B = 1.0F;     // 亮青（内层）
    private static final List<BlockPos> CACHED_TREASURES = new ArrayList<>();  // 容器（金色）
    private static final List<BlockPos> CACHED_ARCHAEOLOGY = new ArrayList<>(); // 考古方块（青色，2026-08-27）
    private static final List<BlockPos> CACHED_SPAWNERS = new ArrayList<>();   // 刷怪笼（品红）
    private static int scanTimer = 0;

    // ============ 已开容器过滤（★ 2026-10-11）============
    /** 本次会话已打开过的战利品容器（客户端本地记忆；进服/换维度/重连由 {@link #resetSession()} 清空）。 */
    private static final Set<GlobalPos> OPENED_CONTAINERS = new HashSet<>();
    /** 待确认的「开箱」目标：右键容器方块时记下，等容器界面<b>真的</b>打开才落库（见 {@link #markOpened}）。 */
    private static GlobalPos pendingOpen = null;
    /** 待确认的时间戳（{@link ClientSession#now()} 口径） */
    private static long pendingOpenAt = 0L;
    /** 待确认有效期（tick）：右键后这么久还没打开容器界面 → 判定为误判并丢弃 */
    private static final int PENDING_TTL_TICKS = 40;

    /** 水平 64 格 + 垂直 128 格的椭球范围判定（y 轴翻倍） */
    private static boolean withinRange(BlockPos center, double x, double y, double z) {
        double dx = x - center.getX();
        double dy = y - center.getY();
        double dz = z - center.getZ();
        return dx * dx + dz * dz <= SCAN_RADIUS_SQ && dy * dy <= SCAN_Y_RADIUS_SQ;
    }

    /** 每秒扫描一次 64 格内的战利品容器/考古刷扫点 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer)) {
            return;
        }
        // ★ 2026-10-11 每 tick 确认「已开容器」——不能放进 20 tick 的扫描里，否则开箱后要等 1 秒才反应
        if (pendingOpen != null) {
            if (ClientSession.now() - pendingOpenAt > PENDING_TTL_TICKS) {
                pendingOpen = null; // 超时仍未打开容器界面 → 判为误判（手持方块右键等），丢弃
            } else if (isContainerScreenOpen()) {
                markOpened(pendingOpen);
                pendingOpen = null;
            }
        }
        scanTimer++;
        if (scanTimer < 20) {
            return;
        }
        scanTimer = 0;
        CACHED_TREASURES.clear();
        CACHED_ARCHAEOLOGY.clear();
        CACHED_SPAWNERS.clear();
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.TREASURE_HUNTER)) {
            return; // 技能未学/未开启
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos center = mc.player.blockPosition();
        // ===== 1. 方块实体：按区块扫描（Level 无公开 blockEntityList；LevelChunk.getBlockEntities 提供全部方块实体） =====
        int chunkMinX = (center.getX() - 64) >> 4;
        int chunkMaxX = (center.getX() + 64) >> 4;
        int chunkMinZ = (center.getZ() - 64) >> 4;
        int chunkMaxZ = (center.getZ() + 64) >> 4;
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = mc.level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!withinRange(center, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                        continue; // 超出范围（水平 64 格 / 垂直 128 格）
                    }
                    // 标记目标：
                    //  金色 = 容器（含模组容器，Container 接口）
                    //  青色 = 考古刷扫点（可疑沙砾/沙子）
                    //  品红 = 刷怪笼/试炼刷怪笼
                    // ⚠️ 2026-08-27：只标 RandomizableContainer 会漏掉无战利品表的普通箱子。
                    if (be instanceof net.minecraft.world.Container) {
                        // ★ 2026-10-11：过滤开启时，本次会话已打开过的容器不再加入高亮列表
                        if (!isOpenedHidden(pos)) {
                            CACHED_TREASURES.add(pos);
                        }
                    } else if (be instanceof BrushableBlockEntity) {
                        CACHED_ARCHAEOLOGY.add(pos); // 考古刷扫点：青色（样式区分，2026-08-27）
                    } else if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity) {
                        CACHED_SPAWNERS.add(pos); // 刷怪笼/试炼刷怪笼（品红色区分，2026-08-27）
                    }
                }
            }
        }
        // ===== 2. 容器实体（2026-08-27 测试反馈：宝箱矿车等漏标）：ContainerEntity 实体（宝箱矿车/漏斗矿车） =====
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity)) {
                continue;
            }
            net.minecraft.world.phys.Vec3 pos = entity.position();
            if (!withinRange(center, pos.x(), pos.y(), pos.z())) {
                continue; // 超出范围（水平 64 格 / 垂直 128 格）
            }
            CACHED_TREASURES.add(net.minecraft.core.BlockPos.containing(pos));
        }
    }

    /**
     * 方块实体渲染后画发光轮廓（白色半透明线框，类似原版发光）。
     * ⚠️ 阶段必须用 AFTER_BLOCK_ENTITIES（其 poseStack 带相机世界变换，translate(-cam) 后能画世界坐标）；
     *     AFTER_LEVEL 的 poseStack 已复原无相机变换 → 画出来不可见（2026-08-27 测试反馈修复）。
     * ⚠️ Iris 兼容：阴影 pass 时跳过（主 pass 才画轮廓，避免阴影里闪烁）。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // Iris 光影软检测：光影激活且处于阴影 pass → 跳过（主 pass 才画轮廓）
        if (isIrisShadowPass()) {
            return;
        }
        if (CACHED_TREASURES.isEmpty() && CACHED_ARCHAEOLOGY.isEmpty() && CACHED_SPAWNERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 相机偏移（渲染坐标 = 世界坐标 - 相机位置）
        var cam = event.getCamera();
        double camX = cam.getPosition().x;
        double camY = cam.getPosition().y;
        double camZ = cam.getPosition().z;

        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        // ⚠️ 2026-08-27 修复（v1.3.1）：不加 disableDepthTest → 深度测试默认 LEQUAL → 被方块挡住
        //    的部分不显示 = 不能透视。1.20.1 已验证 disableDepthTest 全局开关是稳定穿墙方案，同步过来。
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        // ★ 2026-10-11：按当前模式决定显示哪几类（0 全部 / 1 仅容器 / 2 仅考古点 / 3 仅刷怪笼）
        //   ⚠️ 在【渲染处】过滤而不是扫描处 —— 模式切换立即生效，无需等下一次 20 tick 扫描
        final int treasureMode = ModKeyBindingEvents.getSkillModeClient(Skills.TREASURE_HUNTER);
        final List<BlockPos> treasures = (treasureMode == Skills.TREASURE_MODE_ALL
                || treasureMode == Skills.TREASURE_MODE_CONTAINER) ? CACHED_TREASURES : List.of();
        final List<BlockPos> archaeology = (treasureMode == Skills.TREASURE_MODE_ALL
                || treasureMode == Skills.TREASURE_MODE_ARCHAEOLOGY) ? CACHED_ARCHAEOLOGY : List.of();
        final List<BlockPos> spawners = (treasureMode == Skills.TREASURE_MODE_ALL
                || treasureMode == Skills.TREASURE_MODE_SPAWNER) ? CACHED_SPAWNERS : List.of();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        // ===== 容器：金色双线框 =====
        for (BlockPos pos : treasures) {
            // 双线框加粗：外框（扩 0.12 格，暗金）+ 内框（扩 0.03 格，亮金）→ 视觉粗线
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层暗金（粗）：向外扩 0.12
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x0 + 1.12F, y0 + 1.12F, z0 + 1.12F,
                    OUTER_R, OUTER_G, OUTER_B, OUTLINE_ALPHA);
            // 内层亮金（细高亮）：向外扩 0.03
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x0 + 1.03F, y0 + 1.03F, z0 + 1.03F,
                    INNER_R, INNER_G, INNER_B, OUTLINE_ALPHA);
        }
        // ===== 考古方块：青色双线框（样式区分，2026-08-27）=====
        for (BlockPos pos : archaeology) {
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层深青（粗）：向外扩 0.12
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x0 + 1.12F, y0 + 1.12F, z0 + 1.12F,
                    ARCH_OUTER_R, ARCH_OUTER_G, ARCH_OUTER_B, OUTLINE_ALPHA);
            // 内层亮青（细高亮）：向外扩 0.03
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x0 + 1.03F, y0 + 1.03F, z0 + 1.03F,
                    ARCH_INNER_R, ARCH_INNER_G, ARCH_INNER_B, OUTLINE_ALPHA);
        }
        // ===== 刷怪笼：品红色双线框（样式区分，2026-08-27）=====
        for (BlockPos pos : spawners) {
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层深品红（粗）：向外扩 0.14
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.14F, y0 - 0.14F, z0 - 0.14F,
                    x0 + 1.14F, y0 + 1.14F, z0 + 1.14F,
                    SPAWNER_OUTER_R, SPAWNER_OUTER_G, SPAWNER_OUTER_B, OUTLINE_ALPHA);
            // 内层亮品红（细高亮）：向外扩 0.04
            LevelRenderer.renderLineBox(poseStack, buffer,
                    x0 - 0.04F, y0 - 0.04F, z0 - 0.04F,
                    x0 + 1.04F, y0 + 1.04F, z0 + 1.04F,
                    SPAWNER_INNER_R, SPAWNER_INNER_G, SPAWNER_INNER_B, OUTLINE_ALPHA);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest(); // 恢复深度测试，不影响后续渲染
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * Iris 光影软检测（无 Iris 时返回 false）：阴影 pass → 跳过渲染。
     * <p>⚠️ 2026-09-11：改用共享 {@link IrisCompat}。原实现<b>完全无缓存</b>，
     * 每帧执行 {@code Class.forName} + 3 次 {@code getMethod}（每次返回新 Method 副本）；
     * 未装 Iris 时每帧抛 ClassNotFoundException。
     */
    private static boolean isIrisShadowPass() {
        return IrisCompat.isShadowPass();
    }

    // ============ 已开容器过滤（★ 2026-10-11）============

    /** 会话重置（由 {@link ClientSession#resetSession()} 登记调用）：已开容器属于「本次会话记忆」→ 清空。 */
    public static void resetSession() {
        OPENED_CONTAINERS.clear();
        pendingOpen = null;
        pendingOpenAt = 0L;
    }

    /**
     * 右键方块（客户端）→ 只记下「待确认开箱目标」（★ 2026-10-11）。
     *
     * <p>⚠️ <b>不在这里直接落库</b>：右键不一定打开容器（手持方块会放置、潜行另有用语义）
     * → 等容器界面<b>真的</b>打开时再由 {@link #onPlayerTick} 确认，把误判降到零。
     * <p>信号可靠性：已确认 {@code MultiPlayerGameMode.performUseItemOn} 会调
     * {@code CommonHooks.onRightClickBlock} → 本事件在<b>客户端也会触发</b>。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return; // 已开容器是客户端本地记忆，只处理客户端
        }
        if (event.getEntity() != Minecraft.getInstance().player) {
            return; // 只认本地玩家
        }
        pendingOpen = GlobalPos.of(event.getLevel().dimension(), event.getPos().immutable());
        pendingOpenAt = ClientSession.now();
    }

    /** 当前是否打开了容器界面（玩家背包 / 创造背包不算）—— 「开箱」的确认条件 */
    private static boolean isContainerScreenOpen() {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        return screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
                && !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
    }

    /**
     * 标记容器为「已打开」。
     *
     * <p>⚠️ 双箱子会<b>两半一起标记</b> —— 只标记点中的那一半，另一半还亮着，玩家会以为过滤没生效。
     */
    private static void markOpened(GlobalPos target) {
        OPENED_CONTAINERS.add(target);
        java.util.List<BlockPos> marked = new java.util.ArrayList<>();
        marked.add(target.pos());
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(target.dimension())) {
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(target.pos());
            // ⚠️ getConnectedDirection 对【单个箱子】并不返回 null —— 源码是
            //    `return TYPE == LEFT ? FACING.getClockWise() : FACING.getCounterClockWise();`
            //    → 必须先排掉 SINGLE，否则单箱子会误标记一个邻居位置。
            if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                    != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                net.minecraft.core.Direction other =
                        net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
                if (other != null) {
                    BlockPos partner = target.pos().relative(other);
                    // 再验一次邻居确实是箱子（防"左右半箱"状态与方块不一致的存档残留）
                    if (mc.level.getBlockState(partner).getBlock()
                            instanceof net.minecraft.world.level.block.ChestBlock) {
                        OPENED_CONTAINERS.add(GlobalPos.of(target.dimension(), partner));
                        marked.add(partner);
                    }
                }
            }
        }
        // ★ 立即从高亮缓存移除（不等下一次周期扫描）—— 开箱即消失，与扫描间隔无关。
        //   ⚠️ 只在过滤开启时移除：关闭时扫描会照常把它加回来，先移除会造成闪烁。
        if (ModKeyBindingEvents.isTreasureFilterOnClient()) {
            CACHED_TREASURES.removeAll(marked);
        }
    }

    /** 该位置是否应因「已打开过」而从高亮中隐藏（过滤开关关闭时恒为 false） */
    private static boolean isOpenedHidden(BlockPos pos) {
        if (!ModKeyBindingEvents.isTreasureFilterOnClient()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return OPENED_CONTAINERS.contains(GlobalPos.of(mc.level.dimension(), pos));
    }
}
