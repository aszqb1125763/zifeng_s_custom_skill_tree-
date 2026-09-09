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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
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
    // ⚠️ 2026-09-07 架构调整：绑定容器独立为「子功能」——只要学习了任一容器绑定技能
    //（子枫挪移术 AURA_LOOT_VACUUM / 子枫的搬运术 CONTAINER_HAUL）即可绑定，
    // 不再要求挪移术「已学且开启」。绑定数据存玩家存档（LootVacuum* 字段），
    // 供所有容器技能共享，不受单一技能开关逻辑影响。

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return; // 只在服务端执行绑定副作用
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 条件：主手原版木棍 + 潜行 + 已学任一容器绑定技能（不需要对应技能开启）
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != Items.STICK || !player.isShiftKeyDown()) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        // ⚠️ 2026-09-08 迁入木棍工具层 BIND 模块：需工具总开关开 + 当前模式=BIND（潜行右键绑容器）
        //    工具关（还原木棍）或切到 RANGE 时，潜行右键回到原版行为。
        if (!record.isStickToolOn() || record.getStickToolMode() != Skills.STICK_MODE_BIND) {
            return;
        }
        boolean hasBindSkill = false;
        for (String sid : Skills.ALL_SKILLS) {
            if (Skills.isContainerBindSkill(sid) && record.getLearnedPoints(sid) > 0) {
                hasBindSkill = true;
                break;
            }
        }
        if (!hasBindSkill) {
            return; // 未学任何容器绑定技能 → 木棍+潜行右键仍是原版行为
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        // 目标方块必须提供物品容器能力（IItemHandler：原版箱子/漏斗/模组容器通用）
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        if (handler == null) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null); // 兜底：不区分朝向
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
            // ⚠️ 2026-09-08：解除后回发技能数据，客户端绑定框即时消失
            org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                    org.zifeng.skilltree.network.SkillTreeDataS2CPacket.from(record));
            player.displayClientMessage(Component.translatable("chat.zifeng_s_custom_skill_tree.lootvac_unbind"), false);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        record.setLootVacuumBind(dim, pos.getX(), pos.getY(), pos.getZ(),
                face != null ? face.ordinal() : 0, getContainerName(level, pos));
        markDirty(player);
        // ⚠️ 2026-09-08：绑定后回发技能数据，客户端绑定框即时切到新容器
        org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                org.zifeng.skilltree.network.SkillTreeDataS2CPacket.from(record));
        player.displayClientMessage(Component.translatable("chat.zifeng_s_custom_skill_tree.lootvac_bind",
                getContainerName(level, pos), pos.getX(), pos.getY(), pos.getZ()), false);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 取容器方块显示名（如"箱子"；Sophisticated 等参数化方块名用干净名，2026-09-07） */
    private static String getContainerName(Level level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            String clean = org.zifeng.skilltree.compat.SophisticatedCompat.cleanDisplayName(be);
            if (clean != null) {
                return clean; // Sophisticated 干净名（带 wood 参数渲染，无 %s 残留）
            }
        }
        String name = level.getBlockState(pos).getBlock().getName().getString();
        if (name != null && name.contains("%s")) {
            // 兜底防乱码：参数化模板没渲染好时退回方块注册名（难看但不显示 %s）
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        }
        return name;
    }

    /**
     * 统一插入入口（2026-09-09 重写）：纯容器 IO——取目标 ITEM_HANDLER capability 逐槽插入。
     * 像 AE2 存储总线/管道一样带方向访问（face 优先，null 兜底）；只管"放"，怎么存由容器决定。
     * ⚠️ 不再有任何 Sophisticated 反射特判。
     */
    private static ItemStack insertStackInto(Level level, BlockPos pos, net.minecraft.core.Direction face, ItemStack stack) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        if (handler == null) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null); // 兜底不区分朝向
        }
        if (handler == null) {
            return stack;
        }
        return insertAll(handler, stack);
    }

    /** 用绑定时记录的方向插入（record.getLootVacuumFace()；无记录 face 时用 null） */
    private static ItemStack insertIntoBoundTarget(Level level, BlockPos pos, int faceOrdinal, ItemStack stack) {
        net.minecraft.core.Direction face = faceOrdinal >= 0 && faceOrdinal < net.minecraft.core.Direction.values().length
                ? net.minecraft.core.Direction.values()[faceOrdinal] : null;
        return insertStackInto(level, pos, face, stack);
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
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dim)));
        if (targetLevel == null) {
            return false; // 容器所在维度未加载（服务器没有该维度）
        }
        // ⚠️ 2026-08-24 跨维度确保：目标维度的容器 chunk 可能未加载（玩家在别的维度时容器 chunk 不活跃），
        //    必须强制加载 chunk 才能取到容器 block entity / capability——否则 getCapability 返回 null 跨维度失效
        targetLevel.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
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
            ItemStack leftover = insertIntoBoundTarget(targetLevel, pos, record.getLootVacuumFace(), stack);
            if (leftover.isEmpty()) {
                it.remove(); // 全部塞进容器，不生成掉落实体
            } else {
                drop.setItem(leftover); // 部分塞进，剩余继续正常掉落
                allMoved = false;
            }
        }
        return allMoved;
    }

    /**
     * 通用容器放入（2026-09-09）：直接用 NeoForge 官方 ItemHandlerHelper.insertItem——
     * 所有模组/管道共用的标准"把物品塞进容器"方法，内部自行处理逐槽/堆叠/容量规则。
     * 我们只负责"放"，放不下返回剩余由调用方处理，不掺任何自定义逻辑。
     */
    private static ItemStack insertAll(IItemHandler handler, ItemStack stack) {
        return net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack, false);
    }

    /**
     * 凋落物挪移是否当前生效（技能已学开启 + 已绑定容器）——供吸星大法兼容判断（2026-09-06）。
     */
    public static boolean isVacuumActive(PlayerSkillRecord record) {
        return record != null
                && record.getLearnedPoints(Skills.AURA_LOOT_VACUUM) > 0
                && record.isEnabled(Skills.AURA_LOOT_VACUUM)
                && record.hasLootVacuumBind();
    }

    /**
     * 把单个 ItemStack 尽量塞进该玩家绑定的容器，返回未塞下的剩余。
     * 挪移未生效/容器失效/跨维未加载 → 返回原 stack（调用方按未转移处理）。
     * 供吸星大法（MagnetEvents）与挪移同时开启时直传容器用（2026-09-06）。
     */
    public static ItemStack insertIntoBound(ServerPlayer player, PlayerSkillRecord record, ItemStack stack) {
        if (!isVacuumActive(record)) {
            return stack;
        }
        return insertIntoBoundRaw(player, record, stack);
    }

    /**
     * 把单个 ItemStack 尽量塞进该玩家绑定的容器（搬运术用，2026-09-07）。
     * ⚠️ 只要求「已绑定容器」存在（绑定由子枫挪移术建立，搬运术复用同一目标），
     * 不要求挪移技能本身已开启/已学——与吸星大法直传（要求挪移开启）区分。
     */
    public static ItemStack insertIntoBoundRaw(ServerPlayer player, PlayerSkillRecord record, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || record == null || !record.hasLootVacuumBind()) {
            return stack;
        }
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return stack;
        }
        String dim = record.getLootVacuumDim();
        BlockPos pos = new BlockPos(record.getLootVacuumX(), record.getLootVacuumY(), record.getLootVacuumZ());
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dim)));
        if (targetLevel == null) {
            return stack;
        }
        targetLevel.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // 跨维度确保 chunk 加载
        return insertIntoBoundTarget(targetLevel, pos, record.getLootVacuumFace(), stack);
    }

    /**
     * 从该玩家绑定的容器中提取物品（选区放置取料用，2026-09-08）。
     * 只要求「已绑定容器」存在；逐槽取出与标签匹配的物品最多 count 个，
     * 返回合并后的 stack（可能为 EMPTY）。找不到/容器失效 → EMPTY。
     */
    public static ItemStack extractFromBoundRaw(ServerPlayer player, PlayerSkillRecord record,
                                                java.util.function.Predicate<ItemStack> match, int count) {
        if (player == null || record == null || !record.hasLootVacuumBind() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return ItemStack.EMPTY;
        }
        String dim = record.getLootVacuumDim();
        BlockPos pos = new BlockPos(record.getLootVacuumX(), record.getLootVacuumY(), record.getLootVacuumZ());
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dim)));
        if (targetLevel == null) {
            return ItemStack.EMPTY;
        }
        targetLevel.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        IItemHandler handler = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        int want = count;
        for (int slot = 0; slot < handler.getSlots() && want > 0; slot++) {
            ItemStack in = handler.getStackInSlot(slot);
            if (in.isEmpty() || !match.test(in)) {
                continue;
            }
            ItemStack take = handler.extractItem(slot, want, false);
            if (take.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                result = take;
            } else {
                result.grow(take.getCount());
            }
            want -= take.getCount();
        }
        return result;
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
