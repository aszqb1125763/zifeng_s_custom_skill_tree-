package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;

/**
 * 全局游戏事件（GAME 总线，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>玩家进入世界：重新挂载已学技能的属性修饰符</li>
 *   <li>技能点转换机放置：默认绑定放置者 UUID（仿 wmp-1.7.0 的 OwnerBindingEvents）</li>
 *   <li>技能点转换机破坏：解除绑定</li>
 * </ul>
 */
public class SkillEvents {

    /**
     * 玩家属性注册（MOD 总线，由主类 modEventBus.addListener 显式注册，不带 @SubscribeEvent）：
     * 玩家默认没有 FLYING_SPEED，需手动添加，否则技能加成崩溃。
     */
    public static void registerPlayerAttributes(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, Attributes.FLYING_SPEED)) {
            event.add(EntityType.PLAYER, Attributes.FLYING_SPEED);
        }
        // 1.20.1 玩家默认无 JUMP_STRENGTH（1.21 才合入玩家默认属性）：跳跃强化/跳跃增幅需要
        if (!event.has(EntityType.PLAYER, Attributes.JUMP_STRENGTH)) {
            event.add(EntityType.PLAYER, Attributes.JUMP_STRENGTH);
        }
        // 1.20.1 Forge 的 SWIM_SPEED 属性（玩家默认属性集不含，需手动添加）：游泳技能需要
        if (!event.has(EntityType.PLAYER, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get())) {
            event.add(EntityType.PLAYER, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get());
        }
        // 挖掘效率（1.20.1 原版 Attributes 无此属性，用自定义 ModAttributes.MINING_EFFICIENCY 替代）：防御性添加，确保采掘技能生效
        if (!event.has(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get())) {
            event.add(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get());
        }
        // 自定义物理减伤属性（护甲减伤封顶 80% 后继续叠的独立减伤层）
        if (!event.has(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get())) {
            event.add(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            // ⚠️ 血量保护（2026-08-13 修复）：记录进入时的生命值比例。属性重挂会先清空再恢复
            // MAX_HEALTH（先降到基础值、血量被 clamp 下降，再升回上限但血量不跟随）→ 满血玩家会变残血。
            // 重挂后按原比例恢复：满血保持满血，残血保持残血比例。
            float joinHealth = player.getHealth();
            float joinMax = Math.max(1.0F, player.getMaxHealth());
            float joinRatio = Math.min(1.0F, joinHealth / joinMax);
            // 双保险：先清空所有可能残留的技能修饰符 + 重置飞行速度（不干预 mayfly，避免误关创造模式/其他模组飞行）
            SkillEffects.applyAll(player, new PlayerSkillRecord(player.getUUID()));
            UltimateEvents.resetFlyingSpeed(player);
            // 再按当前存档数据应用
            PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            SkillEffects.applyAll(player, record);
            // 血量补偿：按进入时比例恢复（满血玩家重挂后依然满血）
            player.setHealth(Math.max(0.5F, player.getMaxHealth() * joinRatio));
            // 回发技能数据：客户端缓存（万物挖掘等技能状态判断）进世界即有，无需先打开技能树
            org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                    org.zifeng.skilltree.network.SkillTreeDataS2CPacket.from(record));
            // 磁铁屏蔽区列表回发（2026-09-07 / 2026-09-08 全局共享）：服务器全局区列表——
            // 任何玩家框选的屏蔽区全服生效，进服即同步全量（空则不必要发）
            var zoneData = org.zifeng.skilltree.data.MagnetZoneGlobalData.get(level);
            if (!zoneData.getZones().isEmpty()) {
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                        new org.zifeng.skilltree.network.MagnetExclusionS2CPacket(zoneData.getZones()));
            }
            // 进服欢迎推送（2026-08-29）：模组版本 + 简短简介 + 作者署名（中文名不翻译）+ Modern UI 推荐
            String version = net.minecraftforge.fml.ModList.get().getModContainerById(org.zifeng.skilltree.SkillTreeMod.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.welcome_intro", version));
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.welcome_author"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.welcome_modern_ui"));
        }
    }

    /**
     * 玩家登出/切换存档时清理：确保属性修饰符、终极被动临时状态全部移除，
     * 防止跨存档/跨会话残留（每个存档数据独立，但实体状态必须随玩家退出清空）。
     * 杀戮光环已无实体（客户端纯渲染圆环），无需清理实体。
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // ⚠️ 血量保护（2026-08-13 修复）：清空 MAX_HEALTH 修饰符会把当前血量 clamp 下降，
            // 然后原版把该残血值持久化到 player.dat → 下次进世界就是残血。登出前先把血量
            // 按"清空后的上限"比例重新设置（满血玩家按 20/20 存，进世界再按技能恢复满血）。
            float logoutHealth = player.getHealth();
            float logoutMax = Math.max(1.0F, player.getMaxHealth());
            float logoutRatio = Math.min(1.0F, logoutHealth / logoutMax);
            // 清空该玩家所有技能属性修饰符（空 record 等价于全部移除）
            SkillEffects.applyAll(player, new PlayerSkillRecord(player.getUUID()));
            // 登出前按清空后的基础上限恢复血量比例，避免 player.dat 存档残血
            player.setHealth(Math.max(0.5F, player.getMaxHealth() * logoutRatio));
            // 飞行权限不回收：宇宙的青睐点亮状态存在存档里，重进后 tick 自动重新授予，
            // 保留 mayfly=true 让原版 player.dat 持久化，进出存档飞行不丢（只有关闭技能时才回收）
            // UltimateEvents.clearPlayerFlight(player);
            // 重置飞行速度防跨存档残留（flyingSpeed 会被原版持久化到 player.dat）
            UltimateEvents.resetFlyingSpeed(player);
            // 再清理终极被动 static 状态（连击/金身冷却）+ 移除连击攻速修饰符
            UltimateEvents.clearPlayer(player);
            // 清理时之环/晴空环全局锁定计数（防残留导致 gamerule 永远锁死）
            AuraEvents.onPlayerLogout(player);
            // 清理子枫的馈赠在线计时累计（防残留，下次进世界重新计时）
            GiftEvents.onPlayerLogout(player);
            // 清理闪现冷却（防残留）
            BLINK_LAST.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player) {
            if (level.getBlockEntity(event.getPos()) instanceof SkillPointConverterBlockEntity converter) {
                converter.setOwnerUUID(player.getUUID());
                PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
                data.bindMachine(machineKey(level, event.getPos()), player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.getBlockEntity(event.getPos()) instanceof SkillPointConverterBlockEntity) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
                data.unbindMachine(machineKey(level, event.getPos()));
            }
        }
    }

    /**
     * 暴食（GLUTTONY，2026-09-06）：秒吃所有食物。
     * 原版吃东西要走 LivingEntityUseItemEvent（使用进度 tick 递减至 0 → Finish）。
     * 这里在每个 Tick 把剩余时长压到 1 → 下一 tick 立即 Finish 完成食用（含所有模组食物）。
     * 只作用于可食用物品（getFoodProperties != null），弓箭/盾牌/药水等不受影响。
     */
    @SubscribeEvent
    public static void onItemUseTick(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (sp.serverLevel() == null) {
            return; // 登出瞬间防御
        }
        PlayerSkillRecord record = PlayerSkillSavedData.get(sp.serverLevel()).getOrCreatePlayer(sp.getUUID());
        if (record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.GLUTTONY) <= 0
                || !record.isEnabled(org.zifeng.skilltree.skill.Skills.GLUTTONY)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // 仅可食用物品加速（1.20.1/1.21.1 同签名：Item#getFoodProperties(ItemStack, LivingEntity)）
        if (stack.getItem().getFoodProperties(stack, sp) == null) {
            return;
        }
        if (event.getDuration() > 1) {
            event.setDuration(1); // 压到 1 tick → 下一 tick Finish → 秒吃
        }
    }

    // ============ 闪现（BLINK，2026-09-06）：向视线方向传送 ============
    /** 闪现冷却：玩家 UUID → 上次传送的世界时间戳（冷却 2 tick 防连点）
     *  ⚠️ 2026-09-07 修复：原用 player.tickCount（实体时间），玩家死亡重生后新 ServerPlayer
     *  的 tickCount 从 0 重计 → now - last 为巨大负数 < 2 恒成立 → 闪现永久失效直到重启。
     *  改用 level().getGameTime()（世界时间单调递增，与凤凰涅槃/全能精通等冷却一致）。 */
    private static final java.util.Map<java.util.UUID, Long> BLINK_LAST = new java.util.HashMap<>();

    /**
     * 闪现（服务端权威，BlinkC2SPacket 调用）：
     * 向玩家当前视线方向传送——沿视线每 0.5 格采样，最多 100 格；
     * 只判空气/流体（可站），连续实心方块 ≤5 格厚（10 步）允许穿过，超过则停在墙前最后空旷处。
     * 参考原版末影珍珠/EnderIO 旅行手杖的传送语义：传送到视线终点，落地不做额外找地（简单版）。
     */
    public static void blink(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return;
        }
        PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
        if (record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.BLINK) <= 0
                || !record.isEnabled(org.zifeng.skilltree.skill.Skills.BLINK)) {
            return; // 未学/关闭
        }
        // 冷却 2 tick（用世界时间戳，死亡重生不重置；2026-09-07 原 tickCount 死亡后永久拦截）
        long now = player.level().getGameTime();
        Long last = BLINK_LAST.get(player.getUUID());
        if (last != null && now - last < 2) {
            return;
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = player.getLookAngle();
        if (dir.lengthSqr() < 1.0E-6) {
            dir = new net.minecraft.world.phys.Vec3(1, 0, 0); // 保险：垂直视角时避免除零
        }
        net.minecraft.world.phys.Vec3 startPos = player.position();
        net.minecraft.world.level.Level lvl = player.level();
        // ===== 最终规则（2026-09-06）：先 raycast 找第一个实心命中；远处只停面外，近处(≤5格)才穿墙 =====
        // 防“卡碰撞箱被原版推挤穿墙”：任何落点都必须空气 + 玩家 bbox 无碰撞
        net.minecraft.world.phys.Vec3 rayEnd = eye.add(dir.scale(100.0));
        // 1.20.1：ClipContext 第 5 参是 Entity（1.21.1 才是 CollisionContext）；Level.clip(ClipContext) 单参存在
        net.minecraft.world.phys.BlockHitResult hit = lvl.clip(new net.minecraft.world.level.ClipContext(
                eye, rayEnd,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player));
        net.minecraft.world.phys.Vec3 dest = null;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // 命中距离（玩家眼睛 → 命中点，格）
            double dist = eye.distanceTo(hit.getLocation());
            if (dist <= 5.0) {
                // ===== 近距离（墙就在眼前 ≤5 格）：穿墙模式 =====
                // 从眼睛沿视线步进（0.5 格）：命中面前全是开阔(跳过)；碰到第一个实心=进入墙面；
                // 墙内累计实心半格；一旦重新开阔 = 穿出墙 → 停在墙后第一个
                // “空气 + bbox 完全无碰撞”采样点；若连续实心 >6 格(12 半格，>5 格规则)仍未出墙 = 穿不过。
                // 兜底：从命中面沿视线反方向(-dir，=射线来向的空气侧)逐 0.5 格回退找最近安全空气点
                net.minecraft.world.phys.Vec3 wallFallback = null;
                for (int back = 0; back <= 8; back++) {
                    net.minecraft.world.phys.Vec3 cand = hit.getLocation().subtract(dir.scale(0.3 + back * 0.5));
                    wallFallback = resolveSafePoint(lvl, player, startPos, cand);
                    if (wallFallback != null) {
                        break;
                    }
                }
                dest = null;
                int wallRun = 0; // 墙内连续实心半格计数
                boolean inWall = false;
                for (int i = 1; i <= 200; i++) {
                    net.minecraft.world.phys.Vec3 p = eye.add(dir.scale(i * 0.5));
                    net.minecraft.world.level.block.state.BlockState st = lvl.getBlockState(BlockPos.containing(p));
                    boolean open = st.isAir() || !st.getFluidState().isEmpty();
                    if (open) {
                        if (inWall) {
                            // 穿出墙：墙后第一安全格即停
                            net.minecraft.world.phys.Vec3 sp = resolveSafePoint(lvl, player, startPos, p);
                            if (sp != null) {
                                dest = sp;
                                break;
                            }
                            // 刚出墙但 bbox 仍贴墙未完全脱离 → 继续前进半格再试
                        }
                        continue; // 命中面前的开阔：跳过
                    }
                    if (!inWall) {
                        inWall = true; // 碰到墙的近面
                        wallRun = 0;
                        continue;
                    }
                    if (++wallRun > 12) {
                        break; // 连续实心 >6 格仍没出去：穿不过
                    }
                }
                if (dest == null) {
                    dest = wallFallback; // 穿不过/无安全出口 → 停墙面前方
                }
            } else {
                // ===== 远距离（命中 >5 格）：不穿墙，停在“看到的面”外侧空气 =====
                // 面外侧 = 视线来向(-dir)：朝下看地 → 地面顶面之上；朝上看天花板 → 底面之下；看墙 → 墙前
                for (int back = 0; back <= 10; back++) {
                    net.minecraft.world.phys.Vec3 cand = hit.getLocation().subtract(dir.scale(0.5 + back * 0.5));
                    dest = resolveSafePoint(lvl, player, startPos, cand);
                    if (dest != null) {
                        break;
                    }
                }
            }
        } else {
            // ===== 无命中（天空/开阔）：传到 100 格尽头，落在安全空气点 =====
            dest = resolveSafePoint(lvl, player, startPos, rayEnd);
            if (dest == null) {
                // 尽头贴近实体/方块的极端情况：往回找最近安全点
                for (int back = 1; back <= 20; back++) {
                    net.minecraft.world.phys.Vec3 cand = rayEnd.subtract(dir.scale(back * 0.5));
                    dest = resolveSafePoint(lvl, player, startPos, cand);
                    if (dest != null) {
                        break;
                    }
                }
            }
        }
        if (dest == null) {
            return; // 找不到任何安全落点（极端环境），不传送
        }
        BlockPos destPos = BlockPos.containing(dest);
        // 末影传送粒子（落点）+ 显式末影传送音效（原版 SoundEvents.ENDERMAN_TELEPORT）
        player.level().globalLevelEvent(2003, destPos, 0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        // 传送到落点格底部中心（脚贴地）；参考 EnderIO 收尾防回弹
        player.teleportTo(destPos.getX() + 0.5, destPos.getY(), destPos.getZ() + 0.5);
        if (player.connection != null) {
            player.connection.resetPosition();
        }
        player.fallDistance = 0;
        BLINK_LAST.put(player.getUUID(), now);
    }

    /**
     * 闪现辅助（2026-09-06）：给定一个候选点，若该点所在格为空气/流体且玩家整个 bbox 移过去
     * 无碰撞（noCollision，收 0.05 容差）→ 返回该候选点；否则返回 null（表示会卡碰撞箱，不可停）。
     * 这是闪现“永不把碰撞箱嵌进实心方块”的红线校验，防原版推挤把玩家挤出墙。
     */
    private static net.minecraft.world.phys.Vec3 resolveSafePoint(
            net.minecraft.world.level.Level lvl, ServerPlayer player,
            net.minecraft.world.phys.Vec3 startPos, net.minecraft.world.phys.Vec3 p) {
        net.minecraft.world.level.block.state.BlockState st = lvl.getBlockState(BlockPos.containing(p));
        boolean open = st.isAir() || !st.getFluidState().isEmpty();
        if (!open) {
            return null;
        }
        net.minecraft.world.phys.AABB box = player.getBoundingBox()
                .move(p.x - startPos.x, p.y - startPos.y, p.z - startPos.z);
        if (!lvl.noCollision(player, box.inflate(-0.05))) {
            return null;
        }
        return p;
    }

    /** 机器 key：维度|X|Y|Z（与 wmp 相同方案） */
    private static String machineKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }
}
