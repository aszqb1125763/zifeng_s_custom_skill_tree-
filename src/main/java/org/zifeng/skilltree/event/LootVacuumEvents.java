package org.zifeng.skilltree.event;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.items.IItemHandler;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.Collection;
import java.util.Iterator;

/**
 * 凋落物挪移（AURA_LOOT_VACUUM，2026-08-24）：
 * 手持原版木棍蹲下右键任意容器（箱子/漏斗/模组容器）绑定，
 * 之后击杀生物/挖掘方块的掉落物直接传送进绑定的容器——
 * 不生成掉落物实体（ItemEntity），刷怪塔/挖矿机场景不卡顿。
 * <p>绑定逻辑参考 JustDireThings 的 DROPTELEPORT。
 * <p>⚠️ 2026-08-24 修复：绑定信息存【玩家存档 PlayerSkillRecord】（不是物品上），
 * 木棍只是绑定媒介——绑定后无需手持木棍，任何手持状态下击杀/挖掘都会转移掉落物。
 */
public final class LootVacuumEvents {
    private LootVacuumEvents() {
    }

    // ============ 绑定：手持木棍 + 潜行 + 右键容器 ============

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return; // 只在服务端执行绑定副作用
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 条件：主手原版木棍 + 潜行 + 技能已学且开启
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != Items.STICK || !player.isShiftKeyDown()) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (record.getLearnedPoints(Skills.AURA_LOOT_VACUUM) <= 0 || !record.isEnabled(Skills.AURA_LOOT_VACUUM)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        // 目标方块必须提供物品容器能力（IItemHandler：原版箱子/漏斗/模组容器通用）
        // 1.20.1：通过 BlockEntity 获取 capability（Level.getCapability(BlockCapability, BlockPos, Direction) 是 1.21 API）
        net.minecraft.world.level.block.entity.BlockEntity targetBE = level.getBlockEntity(pos);
        IItemHandler handler = null;
        if (targetBE != null) {
            handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            if (handler == null) {
                handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).orElse(null); // 兜底：不区分朝向
            }
        }
        if (handler == null) {
            return; // 不是容器
        }
        // 绑定/解除：同一容器再绑一次 = 解除（参考 JustDireThings）
        String dim = level.dimension().location().toString();
        boolean same = record.hasLootVacuumBind()
                && record.getLootVacuumDim().equals(dim)
                && record.getLootVacuumX() == pos.getX()
                && record.getLootVacuumY() == pos.getY()
                && record.getLootVacuumZ() == pos.getZ();
        if (same) {
            record.clearLootVacuumBind();
            markDirty(player);
            player.displayClientMessage(Component.translatable("chat.zifeng_s_custom_skill_tree.lootvac_unbind"), false);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        record.setLootVacuumBind(dim, pos.getX(), pos.getY(), pos.getZ(),
                face != null ? face.ordinal() : 0, getContainerName(level, pos));
        markDirty(player);
        player.displayClientMessage(Component.translatable("chat.zifeng_s_custom_skill_tree.lootvac_bind",
                getContainerName(level, pos), pos.getX(), pos.getY(), pos.getZ()), false);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 取容器方块显示名（如"箱子"） */
    private static String getContainerName(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    // ============ 掉落传送：击杀/挖掘时把掉落物塞进绑定容器 ============

    /**
     * 尝试把掉落物全部传送进玩家绑定的容器。
     * <p>⚠️ 2026-08-24：绑定信息从玩家存档读取，不要求手持木棍。
     * @param player 击杀/挖掘的玩家
     * @param record 玩家技能记录
     * @param drops  掉落物列表（可从中移除元素）
     * @return true = 全部掉落物都送进容器（调用方可取消掉落实体生成）
     */
    public static boolean tryVacuumDrops(ServerPlayer player, PlayerSkillRecord record,
                                         Collection<ItemEntity> drops) {
        if (player == null || record == null || drops == null || drops.isEmpty()) {
            return false;
        }
        if (record.getLearnedPoints(Skills.AURA_LOOT_VACUUM) <= 0 || !record.isEnabled(Skills.AURA_LOOT_VACUUM)) {
            return false; // 技能未学或未开启
        }
        if (!record.hasLootVacuumBind()) {
            return false; // 未绑定容器
        }
        String dim = record.getLootVacuumDim();
        BlockPos pos = new BlockPos(record.getLootVacuumX(), record.getLootVacuumY(), record.getLootVacuumZ());
        int faceOrdinal = record.getLootVacuumFace();
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.tryParse(dim)));
        if (targetLevel == null) {
            return false; // 容器所在维度未加载（服务器没有该维度）
        }
        // ⚠️ 2026-08-24 跨维度确保：目标维度的容器 chunk 可能未加载（玩家在别的维度时容器 chunk 不活跃），
        //    必须强制加载 chunk 才能取到容器 block entity / capability——否则 getCapability 返回 null 跨维度失效
        targetLevel.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Direction face = faceOrdinal >= 0 && faceOrdinal < Direction.values().length
                ? Direction.values()[faceOrdinal] : null;
        // 1.20.1：通过 BlockEntity 获取 capability
        net.minecraft.world.level.block.entity.BlockEntity targetBE = targetLevel.getBlockEntity(pos);
        IItemHandler handler = null;
        if (targetBE != null) {
            handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            if (handler == null) {
                handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            }
        }
        if (handler == null) {
            return false; // 容器被移除/加载失败
        }
        boolean allMoved = true;
        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            if (drop == null || drop.isRemoved()) {
                continue;
            }
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                it.remove();
                continue;
            }
            ItemStack leftover = insertAll(handler, stack);
            if (leftover.isEmpty()) {
                it.remove(); // 全部塞进容器，不生成掉落实体
            } else {
                drop.setItem(leftover); // 部分塞进，剩余继续正常掉落
                allMoved = false;
            }
        }
        return allMoved;
    }

    /** 把物品尽量塞进容器全部槽位，返回未塞下的剩余（模拟=false 真实插入） */
    private static ItemStack insertAll(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /** ItemStack 列表版（1.20.1 方块掉落 BreakEvent 用）：全部塞进容器返回 true（调用方清空列表），部分塞进返回 false */

    /** 是否可能触发凋落物挪移（技能已学开启 + 已绑定容器）——供 GLM 判断是否需要进入掉落处理 */
    public static boolean hasBinding(ServerPlayer player, PlayerSkillRecord record) {
        if (player == null || record == null) {
            return false;
        }
        return record.getLearnedPoints(Skills.AURA_LOOT_VACUUM) > 0
                && record.isEnabled(Skills.AURA_LOOT_VACUUM)
                && record.hasLootVacuumBind();
    }

    public static boolean tryVacuumDropsStacks(ServerPlayer player, PlayerSkillRecord record,
                                               java.util.List<ItemStack> drops) {
        if (player == null || record == null || drops == null || drops.isEmpty()) {
            return false;
        }
        if (record.getLearnedPoints(Skills.AURA_LOOT_VACUUM) <= 0 || !record.isEnabled(Skills.AURA_LOOT_VACUUM)) {
            return false; // 技能未学或未开启
        }
        if (!record.hasLootVacuumBind()) {
            return false; // 未绑定容器
        }
        String dim = record.getLootVacuumDim();
        BlockPos pos = new BlockPos(record.getLootVacuumX(), record.getLootVacuumY(), record.getLootVacuumZ());
        int faceOrdinal = record.getLootVacuumFace();
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.tryParse(dim)));
        if (targetLevel == null) {
            return false; // 容器所在维度未加载
        }
        targetLevel.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Direction face = faceOrdinal >= 0 && faceOrdinal < Direction.values().length
                ? Direction.values()[faceOrdinal] : null;
        net.minecraft.world.level.block.entity.BlockEntity targetBE = targetLevel.getBlockEntity(pos);
        IItemHandler handler = null;
        if (targetBE != null) {
            handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            if (handler == null) {
                handler = targetBE.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            }
        }
        if (handler == null) {
            return false; // 容器被移除/加载失败
        }
        boolean allMoved = true;
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack leftover = insertAll(handler, stack);
            if (leftover.isEmpty()) {
                it.remove(); // 全部塞进容器
            } else {
                stack.setCount(leftover.getCount()); // 部分塞进，剩余保留（留在掉落列表里正常掉落）
                allMoved = false;
            }
        }
        return allMoved;
    }

    private static void markDirty(ServerPlayer player) {
        if (player.serverLevel() != null) {
            PlayerSkillSavedData.get(player.serverLevel()).setDirty();
        }
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
