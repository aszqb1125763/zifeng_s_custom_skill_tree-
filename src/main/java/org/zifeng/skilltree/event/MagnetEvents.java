package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.UUID;

/**
 * 磁力光环 · 木棍左键拦截（由 SkillTreeMod 手动注册）。
 * <p>⚠️ 2026-09-09 Z-Link 试点迁移：磁铁吸取逻辑（onPlayerTick/attractItems/attractXp）
 * 已【原样】迁移至 {@code system/MagnetModule}，由 ZModules 调度器条件驱动（冬眠调度）。
 * 本类仅保留木棍左键拦截事件（非 tick 逻辑，不属于模块调度范围）。
 */
public class MagnetEvents {

    /**
     * 木棍左键拦截（2026-09-07 磁铁屏蔽区）：持木棍 + 磁铁已学且开启 时，
     * 左键不触发原版挖掘（区块点击被取消）——选区由客户端 tick 射线算角点发 C2S。
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getMainHandItem().is(Items.STICK)) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        // ⚠️ 2026-09-08 迁入工具层 RANGE 模块：需工具开 + 模式=RANGE + 磁铁已学（磁铁开关不影响左键配置）
        if (!record.isStickToolOn() || record.getStickToolMode() != Skills.STICK_MODE_RANGE) {
            return;
        }
        if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0) {
            return;
        }
        event.setCanceled(true); // 不挖方块（保留为选区/删区操作）
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
