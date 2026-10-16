package org.zifeng.skilltree.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 方块扫描·方块标记（★ 2026-10-11，木棍工具 STICK_MODE_MARK 模块）。
 *
 * <p>交互（<b>直接套用容器绑定</b>的判定链与 toggle 语义）：手持【木棍手势载体】+ 潜行 + 右键方块
 * → 把该方块<b>的 id</b> 加入/移出标记集合（同一 id 再标一次 = 取消）。
 *
 * <p>与容器绑定的差别：
 * <ul>
 *   <li>容器绑定存<b>单个坐标</b>（绑定目标唯一）；方块标记存<b>方块 id 集合</b>，<b>数量不设上限</b>。</li>
 *   <li>不要求目标方块有容器能力 —— 任何方块都能标记。</li>
 * </ul>
 *
 * <p>⚠️ 只比对方块 id，不比 NBT / 方块状态（高亮时亦然，见 client/ClientBlockScanEvents）。
 */
public final class BlockMarkEvents {
    private BlockMarkEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return; // 只在服务端写玩家存档
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return; // 潜行 + 右键才是标记（与容器绑定一致）
        }
        if (player.level() == null) {
            return; // 防御：登出瞬间
        }
        ItemStack stack = event.getItemStack();
        PlayerSkillRecord record = PlayerSkillSavedData.get(player.level())
                .getOrCreatePlayer(player.getUUID());
        // ★ 2026-10-09 统一判定：木棍本体 或 本玩家已链接的物品类型（per-player，绝不共享）
        if (!org.zifeng.skilltree.skill.StickGesture.matches(stack, record.getStickLinkedItem())) {
            return;
        }
        // 木棍工具层：需工具总开关开 + 当前模式=方块标记
        if (!record.isStickToolOn() || record.getStickToolMode() != Skills.STICK_MODE_MARK) {
            return;
        }
        // 技能门槛：方块扫描已学（标记模块由该技能解锁，与容器绑定要求「已学任一绑定技能」同款）
        if (record.getLearnedPoints(Skills.BLOCK_SCAN) <= 0) {
            return;
        }
        BlockPos pos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = event.getLevel().getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.AIR) {
            return; // 空气不参与标记
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean nowMarked = record.toggleMarkedBlock(blockId);
        PlayerSkillSavedData.get(player.level()).setDirty();
        // 标记后回发技能数据 → 客户端标记集合即时更新（照抄容器绑定的同步方式）
        org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                org.zifeng.skilltree.network.SkillTreeDataS2CPacket.from(record));
        String blockName = state.getBlock().getName().getString();
        player.sendSystemMessage(Component.translatable(
                        nowMarked
                                ? "chat.zifeng_s_custom_skill_tree.blockscan_mark"
                                : "chat.zifeng_s_custom_skill_tree.blockscan_unmark",
                        blockName, record.getMarkedBlocks().size())
                .withStyle(nowMarked ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        event.getLevel().playSound(null, player.blockPosition(),
                nowMarked ? SoundEvents.END_PORTAL_FRAME_FILL : SoundEvents.ENDER_EYE_DEATH,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
