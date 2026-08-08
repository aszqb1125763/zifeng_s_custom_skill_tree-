package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;

/**
 * 全局游戏事件（GAME 总线，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>玩家进入世界：重新挂载已学技能的属性修饰符</li>
 *   <li>星能转换机放置：默认绑定放置者 UUID（仿 wmp-1.7.0 的 OwnerBindingEvents）</li>
 *   <li>星能转换机破坏：解除绑定</li>
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
        // 原版 Attributes.MINING_EFFICIENCY（NeoForge 合入的挖速加数属性）：防御性添加，确保采掘技能生效
        if (!event.has(EntityType.PLAYER, Attributes.MINING_EFFICIENCY)) {
            event.add(EntityType.PLAYER, Attributes.MINING_EFFICIENCY);
        }
        // 自定义物理减伤属性（护甲减伤封顶 80% 后继续叠的独立减伤层）
        if (!event.has(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION)) {
            event.add(EntityType.PLAYER, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            // 双保险：先清空所有可能残留的技能修饰符 + 重置飞行速度（不干预 mayfly，避免误关创造模式/其他模组飞行）
            SkillEffects.applyAll(player, new PlayerSkillRecord(player.getUUID()));
            UltimateEvents.resetFlyingSpeed(player);
            // 再按当前存档数据应用
            PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            SkillEffects.applyAll(player, record);
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
            // 清空该玩家所有技能属性修饰符（空 record 等价于全部移除）
            SkillEffects.applyAll(player, new PlayerSkillRecord(player.getUUID()));
            // 飞行权限不回收：宇宙的青睐点亮状态存在存档里，重进后 tick 自动重新授予，
            // 保留 mayfly=true 让原版 player.dat 持久化，进出存档飞行不丢（只有关闭技能时才回收）
            // UltimateEvents.clearPlayerFlight(player);
            // 重置飞行速度防跨存档残留（flyingSpeed 会被原版持久化到 player.dat）
            UltimateEvents.resetFlyingSpeed(player);
            // 再清理终极被动 static 状态（连击/金身冷却）+ 移除连击攻速修饰符
            UltimateEvents.clearPlayer(player);
            // 清理时之环/晴空环全局锁定计数（防残留导致 gamerule 永远锁死）
            AuraEvents.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player) {
            if (level.getBlockEntity(event.getPos()) instanceof StarEnergyConverterBlockEntity converter) {
                converter.setOwnerUUID(player.getUUID());
                PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
                data.bindMachine(machineKey(level, event.getPos()), player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.getBlockEntity(event.getPos()) instanceof StarEnergyConverterBlockEntity) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
                data.unbindMachine(machineKey(level, event.getPos()));
            }
        }
    }

    /** 机器 key：维度|X|Y|Z（与 wmp 相同方案） */
    private static String machineKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }
}
