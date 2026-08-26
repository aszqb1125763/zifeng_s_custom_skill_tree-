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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

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
    private static final float OUTLINE_ALPHA = 0.95F; // 发光轮廓不透明度
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
                        CACHED_TREASURES.add(pos);
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
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        // ===== 容器/考古点：金色双线框 =====
        for (BlockPos pos : CACHED_TREASURES) {
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
        for (BlockPos pos : CACHED_ARCHAEOLOGY) {
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
        for (BlockPos pos : CACHED_SPAWNERS) {
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

        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * Iris 光影软检测（无 Iris 时返回 false）：光影激活且处于阴影 pass → 跳过渲染。
     * 反射调用 IrisApi（避免编译期依赖 iris jar）。
     */
    private static boolean isIrisShadowPass() {
        try {
            Class<?> irisApiCls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApiCls.getMethod("getInstance").invoke(null);
            if (!(Boolean) irisApiCls.getMethod("isShaderPackInUse").invoke(instance)) {
                return false; // 无光影激活 → 正常渲染
            }
            return (Boolean) irisApiCls.getMethod("isRenderingShadowPass").invoke(instance);
        } catch (Throwable ignored) {
            return false; // 无 Iris 或 API 变动 → 正常渲染
        }
    }
}
