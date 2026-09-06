package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
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
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) {
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
        // 虚空之矛：已学即提供磁铁范围增幅（55 格，Config 可调，经验和掉落物都生效）
        boolean voidSpear = record.getLearnedPoints(Skills.AURA_VOID) > 0;
        double itemRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_ITEM_RADIUS.get();
        double xpRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_XP_RADIUS.get();
        // ⚠️ 2026-09-06 改版：每 tick 全量吸取（去掉频率门控与单次数量上限）——
        //    配合子枫挪移术直传容器不生成实体，刷怪塔/农场不再卡顿。
        attractItems(player, itemRadius, record);
        attractXp(player, xpRadius);
    }

    /**
     * 吸取掉落物：
     * 与子枫挪移术同时开启（且有绑定容器）→ 掉落物直传绑定容器（不生成实体，防卡顿）；
     * 否则传送到玩家脚下自然掉落（由原版拾取机制自动进背包，背包满则留在地上）。
     */
    private static void attractItems(ServerPlayer player, double radius, PlayerSkillRecord record) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        if (items.isEmpty()) {
            return;
        }
        // 挪移是否同开生效（每 tick 只判断一次，避免逐物品查绑定）
        boolean vacuumActive = org.zifeng.skilltree.event.LootVacuumEvents.isVacuumActive(record);
        boolean any = false;
        for (ItemEntity item : items) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            // 物品有归属（是其他玩家刚丢出的）且不属于自己 → 不吸（不抢别人的东西）
            net.minecraft.world.entity.Entity owner = item.getOwner();
            if (owner != null && !owner.getUUID().equals(player.getUUID()) && item.hasPickUpDelay()) {
                continue;
            }
            if (vacuumActive) {
                // 吸星 + 挪移同开：掉落物直传绑定容器（2026-09-06）
                net.minecraft.world.item.ItemStack leftover =
                        org.zifeng.skilltree.event.LootVacuumEvents.insertIntoBound(player, record, item.getItem());
                if (leftover.isEmpty()) {
                    item.discard(); // 全部进容器
                    any = true;
                    continue;
                }
                // 部分进容器（容器快满）：剩余留在地上继续被吸
                if (leftover.getCount() != item.getItem().getCount()) {
                    item.setItem(leftover);
                }
            }
            // 传送到玩家脚下自然掉落（原版拾取判定由游戏处理：进背包或背包满留在地上）
            item.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
            item.setPickUpDelay(0);
            item.setDeltaMovement(0, 0, 0);
            any = true;
        }
        if (any) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1F, 1.0F + level.random.nextFloat() * 0.1F);
        }
    }

    /** 吸取经验球：直接模拟拾取（尊重 PlayerXpEvent.PickupXp 取消）；2026-09-06 起每 tick 全量无上限 */
    private static void attractXp(ServerPlayer player, double radius) {
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
        if (orbs.isEmpty()) {
            return;
        }
        for (ExperienceOrb orb : orbs) {
            if (!orb.isAlive()) {
                continue;
            }
            // 1.20.1：IEventBus.post 返回 boolean（true=已取消），不是返回 Event
            PlayerXpEvent.PickupXp pickupEvent = new PlayerXpEvent.PickupXp(player, orb);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(pickupEvent)) {
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
