package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * 机械共鸣·区块技能执行器（2026-09-08）：
 * <ul>
 *   <li>选区放置（triggerPlace）：手持物品=标签（决定放什么方块），按触发键执行一次。</li>
 *   <li>选区挖掘（triggerExcavate）：手持工具=标签（只供附魔，不耗耐久），按触发键瞬间挖空全区。</li>
 *   <li>选区攻击：无触发键，开启后 tick 自动攻击区内生物（频率/伤害同杀戮光环）。</li>
 *   <li>防护选区（多块，上限 10）：区内生物对【全服任何人的杀戮光环/选区攻击】不可见（目标扫描剔除）——
 *       只屏蔽杀戮光环伤害（含其范围判定与虚空直杀），不拦怪物/环境/普通近战等其它来源。</li>
 * </ul>
 * 单人生效：操作区存各玩家 PlayerSkillRecord。
 */
public final class ZoneSkillEvents {
    private ZoneSkillEvents() {
    }

    /** 单次放置/挖掘触发的最大方块数（防手滑框超大区把服务器卡死） */
    private static final int MAX_BLOCKS_PER_TRIGGER = 32768;

    // ============ 选区放置：填满整个选区 ============

    /**
     * 触发一次选区放置：遍历操作区每个可放格，用绑定容器优先的方块填满。
     * 需求（2026-09-08）：手持物品当"标签"决定放什么方块；容器优先→手头补→再缺跳过；
     * 单人生效、无粒子。
     */
    public static void triggerPlace(ServerPlayer player, PlayerSkillRecord record) {
        if (!isActive(player, record, Skills.MACHINE_ZONE_PLACE)) {
            return;
        }
        OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_PLACE);
        if (zone == null) {
            return; // 未框选
        }
        // 标签方块 = 主手 BlockItem
        ItemStack tag = player.getMainHandItem();
        if (!(tag.getItem() instanceof BlockItem blockItem)) {
            return; // 必须手持方块类物品当标签
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        BlockState placeState = blockItem.getBlock().defaultBlockState();
        int placed = 0;
        for (BlockPos pos : zone.blockPositions()) {
            if (placed >= MAX_BLOCKS_PER_TRIGGER) {
                break;
            }
            BlockState cur = level.getBlockState(pos);
            if (!cur.isAir() && !cur.canBeReplaced()) {
                continue; // 已有方块且不可替换 → 跳过
            }
            ItemStack material = takeMaterial(player, record, blockItem, 1);
            if (material.isEmpty()) {
                break; // 材料耗尽：跳过剩余（需求：再缺跳过）
            }
            level.setBlock(pos, placeState, 3);
            placed++;
        }
    }

    /**
     * 取 1 个同标签方块：绑定容器优先（不要求挪移开启），再玩家背包/手上补，最后缺 → 空。
     */
    private static ItemStack takeMaterial(ServerPlayer player, PlayerSkillRecord record, BlockItem tag, int count) {
        // ① 绑定容器优先
        if (record.hasLootVacuumBind()) {
            ItemStack fromBound = org.zifeng.skilltree.event.LootVacuumEvents.extractFromBoundRaw(player, record,
                    stack -> stack.getItem() == tag, count);
            if (!fromBound.isEmpty()) {
                return fromBound;
            }
        }
        // ② 手头补（主手/背包逐个找同物品）
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == tag) {
                ItemStack take = stack.split(count);
                return take.isEmpty() ? ItemStack.EMPTY : take;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() == tag) {
            return offhand.split(count);
        }
        return ItemStack.EMPTY;
    }

    // ============ 选区挖掘：瞬间挖空整个选区 ============

    /**
     * 触发一次选区挖掘：操作区内所有非空气方块瞬间移除，不需要进度条、无粒子。
     * 掉落尝试塞绑定容器、多余掉落清除、不生成经验；主手工具只提供附魔（精准采集/时运等），不耗耐久。
     * <p>联动增幅（2026-09-08，全部对齐正常挖掘管线语义，尊重各技能开关/生效等级）：
     * <ul>
     *   <li>自动熔炼（AUTO_SMELT 已学+开启）→ 挖区掉落物先按熔炉配方熔炼再入容器（尊重熔炼黑名单）</li>
     *   <li>点石成金（BLOCK_DROP 开启）→ 仅对吃时运的方块（矿石类，isOreBlock）掉落×倍率</li>
     * </ul>
     * ⚠️ 2026-09-08：不兼容万物挖掘（太 OP）——基岩等无法破坏方块在挖区内被直接忽视、不拆除。
     */
    public static void triggerExcavate(ServerPlayer player, PlayerSkillRecord record) {
        if (!isActive(player, record, Skills.MACHINE_ZONE_EXCAVATE)) {
            return;
        }
        OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_EXCAVATE);
        if (zone == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        // 自动熔炼/点石成金开关（getBlockDropMultiplier 内部已尊重开关+生效等级，未学=1.0）
        boolean smeltOn = record.getLearnedPoints(Skills.AUTO_SMELT) > 0
                && record.isEnabled(Skills.AUTO_SMELT);
        double blockMult = org.zifeng.skilltree.skill.SkillEffects.getBlockDropMultiplier(record);
        int mined = 0;
        for (BlockPos pos : zone.blockPositions()) {
            if (mined >= MAX_BLOCKS_PER_TRIGGER) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            // 流体（水/岩浆，含流动态）：直接清空为空气——不产掉落、不走熔炼/倍率（2026-09-08）
            if (!state.getFluidState().isEmpty()) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                mined++;
                continue;
            }
            if (isUnyielding(state)) {
                continue; // 基岩等无法破坏方块：直接忽视、不拆除（2026-09-08 不兼容万物挖掘，太 OP）
            }
            // 取掉落（附魔生效：精准采集/时运由工具提供）
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
            // 自动熔炼：统一走 UltimateEvents.applyAutoSmelt（唯一入口，含黑名单/配方缓存；
            //   v1-2 收编：删除本地复制版 applyZoneSmelt）
            if (smeltOn && !drops.isEmpty()) {
                org.zifeng.skilltree.event.UltimateEvents.applyAutoSmelt(player, drops, record);
            }
            // 点石成金：仅吃时运的方块（矿石类）掉落×倍率（与正常挖掘语义一致，防泥土/石头刷量）
            if (blockMult > 1.0 && org.zifeng.skilltree.event.UltimateEvents.isOreBlock(state, level)) {
                org.zifeng.skilltree.event.UltimateEvents.applyDropMultiplierStacks(drops, player, blockMult);
            }
            // 移除方块（无掉落生成——掉落入容器由我们手动处理；不触发经验）
            level.removeBlock(pos, false);
            // 掉落：优先塞绑定容器 → 多余清除（不生成 ItemEntity，杜绝掉地上）
            for (ItemStack drop : drops) {
                ItemStack leftover = org.zifeng.skilltree.event.LootVacuumEvents.insertIntoBoundRaw(player, record, drop);
                // leftover 丢弃（需求：多余掉落清除）
            }
            mined++;
        }
    }

    /** 不可被选区挖掘破坏的方块（基岩/末地门框架/末地折跃门/屏障等，参考方块破坏保护） */
    private static boolean isUnyielding(BlockState state) {
        Block b = state.getBlock();
        return b == net.minecraft.world.level.block.Blocks.BEDROCK
                || b == net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME
                || b == net.minecraft.world.level.block.Blocks.END_PORTAL
                || b == net.minecraft.world.level.block.Blocks.END_GATEWAY
                || b == net.minecraft.world.level.block.Blocks.BARRIER
                || b == net.minecraft.world.level.block.Blocks.COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.CHAIN_COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.REPEATING_COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.JIGSAW
                || b == net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK;
    }

    // ============ 选区攻击：开启后自动攻击区内生物（tick 挂载） ============

    /**
     * Z-Link 门面迁移（2026-09-09）：原 onPlayerTick 中"选区攻击"部分抽为 tickZoneAttack，
     * 由 system/ZoneAttackModule 调度调用（学了攻击区技能且有区才唤醒）。内容一字未改。
     */
    public static void tickZoneAttack(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
        PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
        // 攻击区：需技能开启 + 有区
        if (record.getLearnedPoints(Skills.MACHINE_ZONE_ATTACK) > 0
                && record.isEnabled(Skills.MACHINE_ZONE_ATTACK)) {
            OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_ATTACK);
            if (zone != null) {
                zoneAttack(player, record, zone);
            }
        }
        // 防护区：无需每 tick 动作——目标扫描时已按全服防护区剔除（只屏蔽杀戮光环伤害）
    }

    /**
     * 攻击区自动攻击：频率/伤害与杀戮光环完全同款（auraAttackInterval + auraAttackParams），
     * 只把 AOE 范围换成玩家框选的选区；无粒子（dealAuraDamageToOne 内已删粒子）。
     */
    private static void zoneAttack(ServerPlayer player, PlayerSkillRecord record, OperZone zone) {
        int interval = AuraEvents.auraAttackInterval(player, record);
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        float[] p = AuraEvents.auraAttackParams(player, record);
        float damage = p[0];
        boolean voidSpear = p[1] > 0;
        boolean empower = p[2] > 0;
        boolean ignoreIFrames = p[3] > 0;
        ItemStack weapon = player.getMainHandItem();
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        // 扫区内目标（目标过滤同杀戮光环：敌我/所有由目标模式决定；跳过【全服任意布防者】防护区内生物——区域内不判定）
        java.util.List<PlayerSkillRecord> protectors = activeProtectors(level);
        String dim = level.dimension().location().toString();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                zone.aabb(),
                target -> AuraEvents.isTargetValid(player, target, record.getAuraTargetMode(Skills.AURA_DAMAGE))
                        && !inAnyProtectOf(protectors, dim, target.blockPosition()));
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() > 50) {
            targets = new ArrayList<>(targets.subList(0, 50)); // 每轮上限，防刷怪塔卡死
        }
        for (LivingEntity target : targets) {
            AuraEvents.dealAuraDamageToOne(player, level, target, damage, voidSpear, empower, ignoreIFrames, weapon);
        }
    }

    // ============ 防护区：区内生物对玩家光环/攻击完全不可见（多块，上限 10，全服向） ============

    /**
     * 收集已开启防护区且至少有一块的玩家 record 列表（光环触发 tick 只算一次，供过滤复用）。
     * <p><b>⚠️ 2026-09-10 修复（链接玩家，不分维度）</b>：改用全服在线玩家
     * （{@code level.getServer().getPlayerList().getPlayers()}）—— 旧实现用 {@code level.players()}
     * 只拿到【与目标同维度】的布防者，导致其他维度玩家布的防护区失效。
     * 区域本身仍按 dim 匹配（在维度 X 布的防护区只保护维度 X 内的目标）。
     */
    public static java.util.List<PlayerSkillRecord> activeProtectors(ServerLevel level) {
        java.util.List<PlayerSkillRecord> out = new java.util.ArrayList<>();
        if (level == null) {
            return out;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
        net.minecraft.server.MinecraftServer server = level.getServer();
        // ⚠️ 链接玩家：全服在线玩家（不分维度）；server 为 null 时降级为当前维度玩家
        java.util.List<ServerPlayer> online = server != null
                ? server.getPlayerList().getPlayers() : level.players();
        for (ServerPlayer p : online) {
            // ⚠️ 2026-09-11 性能修复：改用只读 getPlayer（不创建）——
            // 原 getOrCreatePlayer 会为【从没学过技能的玩家】创建空记录，
            // 而本方法每次光环攻击触发都跑一遍全服玩家（高频），会白建记录 + 白写存档。
            PlayerSkillRecord rec = data.getPlayer(p.getUUID());
            if (rec == null) {
                continue; // 无技能数据 → 不可能有防护区，跳过
            }
            if (rec.getLearnedPoints(Skills.MACHINE_ZONE_PROTECT) > 0
                    && rec.isEnabled(Skills.MACHINE_ZONE_PROTECT)
                    && !rec.getProtectZones().isEmpty()) {
                out.add(rec);
            }
        }
        return out;
    }

    /** 目标是否位于给定防护者列表（任一块，同维度）内 */
    public static boolean inAnyProtectOf(java.util.List<PlayerSkillRecord> protectors,
                                         String dim, net.minecraft.core.BlockPos pos) {
        for (PlayerSkillRecord rec : protectors) {
            if (rec.isInAnyProtectZone(dim, pos)) {
                return true;
            }
        }
        return false;
    }

    /** 便捷版（少数调用点）：直接查目标是否位于任何在线布防者的防护区内 */
    public static boolean isInAnyProtectZoneGlobal(ServerLevel level, net.minecraft.world.entity.Entity target) {
        if (level == null || target == null || target.level() == null) {
            return false;
        }
        String dim = target.level().dimension().location().toString();
        return inAnyProtectOf(activeProtectors(level), dim, target.blockPosition());
    }

    // ⚠️ 2026-09-08 用户定稿：防护区【只屏蔽杀戮光环伤害】——杀戮光环全部伤害通道
    //   （普通/混沌连击/虚空秒杀直杀等）都从 dealAuraDamageToOne 一个口子走，而该口子上游
    //   的目标扫描已按全服防护区过滤（auraAttack / zoneAttack 的 isInAnyProtectZone 剔除），
    //   源头屏蔽即已完整覆盖。不再设受击事件兜底：否则会误拦怪物/环境/普通玩家近战等
    //   非杀戮光环来源（与"只屏蔽杀戮光环"相悖）。

    // ============ 共用 ============

    private static boolean isActive(ServerPlayer player, PlayerSkillRecord record, String skillId) {
        if (player == null || player.serverLevel() == null || record == null) {
            return false;
        }
        return record.getLearnedPoints(skillId) > 0 && record.isEnabled(skillId);
    }
}
