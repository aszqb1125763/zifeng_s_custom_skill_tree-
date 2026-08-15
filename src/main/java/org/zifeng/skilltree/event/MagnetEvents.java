package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 磁力光环（自写实现，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>光环技能（AURA_MAGNET，一次性解锁），开启后自动吸取范围内的经验球和掉落物</li>
 *   <li>掉落物：传送到玩家脚下自然掉落（由原版拾取机制进背包，背包满则留在地上）</li>
 *   <li>经验球：直接模拟拾取（尊重其他模组取消）</li>
 *   <li>潜行时自动暂停（防止偷取时误吸）</li>
 *   <li>性能优化：每 10 tick 全半径扫描，其余 tick 只扫 5 格</li>
 *   <li>吸取顺序：按距离从近到远（最近的优先吸）</li>
 * </ul>
 */
public class MagnetEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 潜行时自动暂停（防止偷取时误吸）
        if (player.isShiftKeyDown()) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        // 磁力光环技能：需已学习且开关开启（H 键切换，独立于杀戮光环 K 键）
        if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0 || !record.isEnabled(Skills.AURA_MAGNET)) {
            return;
        }
        // ⚠️ 性能优化（2026-08-15）：磁铁恢复原效果（全半径全量吸取），改为每 2 tick（0.1 秒）扫描一次——
        //    比原版每 tick 更省开销，比 10 tick 更灵敏（吸取响应快，不卡顿）。
        if (player.tickCount % 2 != 0) {
            return;
        }
        // 虚空之矛：已学即提供磁铁范围增幅（55 格，Config 可调，经验和掉落物都生效）
        boolean voidSpear = record.getLearnedPoints(Skills.AURA_VOID) > 0;
        double itemRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_ITEM_RADIUS.get();
        double xpRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_XP_RADIUS.get();
        attractItems(player, itemRadius);
        attractXp(player, xpRadius);
    }

    /** 吸取掉落物：传送到玩家脚下自然掉落（由原版拾取机制自动进背包，背包满则留在地上） */
    private static void attractItems(ServerPlayer player, double radius) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        if (items.isEmpty()) {
            return;
        }
        // ⚠️ 性能优化（2026-08-15）：单 tick 处理上限——光环/战利品爆炸场景可能瞬间产生上千掉落物，
        //    一次全部 teleportTo 会导致服务端卡死。限制每次最多处理 N 个（默认 64），其余下 tick 继续。
        int maxPerTick = org.zifeng.skilltree.Config.MAGNET_MAX_PER_TICK.get();
        int processed = 0;
        // 按距离从近到远排序（最近优先吸取）——仅当数量超限时才需要排序（否则顺序遍历零开销）
        if (items.size() > maxPerTick) {
            items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(player)));
        }
        boolean any = false;
        for (ItemEntity item : items) {
            if (processed >= maxPerTick) {
                break;
            }
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            // 物品有归属（是其他玩家刚丢出的）且不属于自己 → 不吸（不抢别人的东西）
            net.minecraft.world.entity.Entity owner = item.getOwner();
            if (owner != null && !owner.getUUID().equals(player.getUUID()) && item.hasPickUpDelay()) {
                continue;
            }
            // 传送到玩家脚下自然掉落（原版拾取判定由游戏处理：进背包或背包满留在地上）
            item.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
            item.setPickUpDelay(0);
            item.setDeltaMovement(0, 0, 0);
            any = true;
            processed++;
        }
        if (any) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1F, 1.0F + level.random.nextFloat() * 0.1F);
        }
    }

    /** 吸取经验球：直接模拟拾取（尊重 PlayerXpEvent.PickupXp 取消） */
    private static void attractXp(ServerPlayer player, double radius) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
        if (orbs.isEmpty()) {
            return;
        }
        // ⚠️ 性能优化（2026-08-15）：单 tick 处理上限（与掉落物共享预算），防止上千经验球同时结算卡顿
        int maxPerTick = org.zifeng.skilltree.Config.MAGNET_MAX_PER_TICK.get();
        int processed = 0;
        // 按距离从近到远排序——仅当数量超限时才需要排序
        if (orbs.size() > maxPerTick) {
            orbs.sort(Comparator.comparingDouble(orb -> orb.distanceToSqr(player)));
        }
        for (ExperienceOrb orb : orbs) {
            if (processed >= maxPerTick) {
                break;
            }
            if (!orb.isAlive()) {
                continue;
            }
            PlayerXpEvent.PickupXp event = NeoForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, orb));
            if (event.isCanceled()) {
                continue;
            }
            player.take(orb, 1);
            player.giveExperiencePoints(orb.value);
            orb.discard();
            processed++;
        }
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
