package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.List;
import java.util.UUID;

/**
 * 磁力光环模块（Z-Link 首个试点迁移，2026-09-09）。
 * <p>原 {@code MagnetEvents.onPlayerTick} 逻辑【原样】迁移到此 —— 功能零变化，
 * 仅触发方式改变：不再每玩家每 tick 都进事件（未学磁铁的玩家也空跑一次判定），
 * 改由 {@link ZModules} 调度器驱动：活跃条件不满足（未学/未开启/潜行暂停）→ 模块冬眠，
 * 扫描完全跳过。性能收益 = "玩家没需求，数据根本不执行"。
 * <p>⚠️ 1.21.1 版：吸经验球用 NeoForge 事件总线 post（与 1.20.1 Forge 写法不同，平台差异仅此一处）。
 */
public final class MagnetModule implements ZModule {
    /** 单例（模块无 per-player 状态，全局一份即可） */
    public static final MagnetModule INSTANCE = new MagnetModule();

    private MagnetModule() {
    }

    @Override
    public String id() {
        return "aura_magnet";
    }

    /** 活跃条件（轻量 map 读/布尔；潜行暂停/未学/未开启 → 冬眠零开销） */
    @Override
    public boolean activeCondition(ServerPlayer player) {
        // 潜行时自动暂停（防止偷取时误吸）
        if (player.isShiftKeyDown()) {
            return false;
        }
        // ⚠️ 2026-09-09 性能审计：只读 getPlayer——无记录（从没学任何技能）→ 冬眠，不建空记录
        PlayerSkillRecord record = player.serverLevel() == null ? null
                : PlayerSkillSavedData.get(player.serverLevel()).getPlayer(player.getUUID());
        if (record == null) {
            return false;
        }
        // 磁力光环技能：需已学习且开关开启（H 键切换，独立于杀戮光环 K 键）
        return record.getLearnedPoints(Skills.AURA_MAGNET) > 0 && record.isEnabled(Skills.AURA_MAGNET);
    }

    /** 活跃时每 tick 执行（逻辑与 MagnetEvents.onPlayerTick 原版一致） */
    @Override
    public void onTick(ServerPlayer player) {
        PlayerSkillRecord record = getRecord(player);
        // 虚空之矛：已学即提供磁铁范围增幅（55 格，Config 可调，经验和掉落物都生效）
        boolean voidSpear = record.getLearnedPoints(Skills.AURA_VOID) > 0;
        double itemRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_ITEM_RADIUS.get();
        double xpRadius = voidSpear ? Config.VOID_MAGNET_RADIUS.get() : Config.MAGNET_XP_RADIUS.get();
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
        // 屏蔽区（2026-09-08 全局共享）：全服同一份列表——任何玩家框选的区对所有磁铁生效。
        //    从服务器全局数据（主世界 SavedData）取；空列表时零开销。
        java.util.List<org.zifeng.skilltree.data.MagnetExclusionZone> zones = null;
        String dimName = level.dimension().location().toString();
        boolean any = false;
        for (ItemEntity item : items) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            // 屏蔽区：物品落在任一屏蔽区内 → 不吸（保护该区域掉落物；经验球不屏蔽）
            if (zones == null) {
                zones = org.zifeng.skilltree.data.MagnetZoneGlobalData.get(player.serverLevel()).getZones(); // 惰性取一次（全服）
            }
            boolean excluded = false;
            for (org.zifeng.skilltree.data.MagnetExclusionZone z : zones) {
                if (z.contains(dimName, item.getX(), item.getY(), item.getZ())) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                continue; // 在屏蔽区内：不吸
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

    /** 吸取经验球：直接模拟拾取（尊重 PlayerXpEvent.PickupXp 取消） */
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
