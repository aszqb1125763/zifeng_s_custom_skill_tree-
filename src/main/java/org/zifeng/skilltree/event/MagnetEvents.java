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

import java.util.List;
import java.util.UUID;

/**
 * 磁力光环（参考龙之研究 Item Dislocator，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>光环技能（AURA_MAGNET，一次性解锁），开启后自动吸取范围内的经验球和掉落物</li>
 *   <li>掉落物：直接瞬移到玩家脚下并清零速度（立刻被拾取）</li>
 *   <li>经验球：直接模拟拾取（take + giveExperiencePoints + discard），尊重其他模组取消</li>
 *   <li>潜行时自动暂停（防止偷取时误吸）</li>
 *   <li>性能优化：每 10 tick 全半径扫描，其余 tick 只扫 5 格</li>
 *   <li>半径：物品与经验独立配置（Config），各自最大 32 格</li>
 * </ul>
 */
public class MagnetEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 潜行时自动暂停（防偷取误吸，参考 Draconic）
        if (player.isShiftKeyDown()) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        // 磁力光环技能：需已学习且开关开启
        if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0 || !record.isEnabled(Skills.AURA_MAGNET)) {
            return;
        }
        // 每 10 tick 全半径扫描，其余 tick 只扫 5 格（性能优化，参考 Draconic）
        boolean fullScan = player.tickCount % 10 == 0;
        double itemRadius = Config.MAGNET_ITEM_RADIUS.get();
        double xpRadius = Config.MAGNET_XP_RADIUS.get();
        attractItems(player, fullScan ? itemRadius : Math.min(5.0, itemRadius));
        attractXp(player, fullScan ? xpRadius : Math.min(5.0, xpRadius));
    }

    /** 吸取掉落物：直接瞬移到玩家脚下（清零速度/延迟，立刻被拾取） */
    private static void attractItems(ServerPlayer player, double radius) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        // 性能优化：一次查询玩家周围是否有其他玩家（单人零开销，多人精确判定不抢战利品）
        boolean anyOtherPlayer = !level.getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(radius + 4.0), p -> p != player && p.isAlive()).isEmpty();
        boolean any = false;
        for (ItemEntity item : items) {
            if (!item.isAlive()) {
                continue;
            }
            // 尊重其他模组的"防远程吸取"标记（Draconic 做法）
            if (item.getPersistentData().contains("PreventRemoteMovement")) {
                continue;
            }
            // 玩家刚丢出的物品（hasPickUpDelay>0）不吸回；1.21.1 getOwner() 返回 Entity
            net.minecraft.world.entity.Entity owner = item.getOwner();
            if (owner != null && owner.getUUID().equals(player.getUUID()) && item.hasPickUpDelay()) {
                continue;
            }
            // 附近 4 格内有其他玩家 → 不抢别人的战利品（仅当附近确有他人才精确判断）
            if (anyOtherPlayer) {
                Player closest = level.getNearestPlayer(item, 4);
                if (closest != null && closest != player) {
                    continue;
                }
            }
            if (player.distanceToSqr(item) > 4.0) {
                any = true;
            }
            if (item.hasPickUpDelay()) {
                item.setNoPickUpDelay();
            }
            // 性能优化：只在物品距玩家超过 1.5 格时才瞬移（近处物品自然被拾取，减少 setPos 触发区块操作）
            if (player.distanceToSqr(item) > 2.25) {
                item.setDeltaMovement(0, 0, 0);
                item.fallDistance = 0;
                item.setPos(player.getX() - 0.2 + level.random.nextDouble() * 0.4,
                        player.getY() - 0.6,
                        player.getZ() - 0.2 + level.random.nextDouble() * 0.4);
            }
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
        for (ExperienceOrb orb : orbs) {
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
