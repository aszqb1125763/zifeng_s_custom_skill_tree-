package org.zifeng.skilltree.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 万物挖掘（ULT_BREAK_ALL，2026-08-13 需求）：把基岩等原版不可破坏方块（getDestroyProgress 返回 0）
 * 的挖掘进度替换为【黑曜石的挖掘进度】→ 玩家用镐子按住即可挖掘（客户端破坏动画 + 服务端破坏流程都能跑通）。
 * <p>
 * ⚠️ 目标类必须是 {@link BlockBehaviour.BlockStateBase}（getDestroyProgress 定义在父类，
 * BlockState 未重写）——之前 Mixin 到 BlockState 找不到方法，require=0 静默失败，基岩进度永远是 0。
 * <p>
 * 只对「技能已学且开启 + 手持镐子」的玩家生效；正常可破坏方块不受影响（返回即走）。
 * 客户端/服务端两侧都注入（两侧都会计算挖掘进度）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true, require = 0)
    private void zifeng$breakUnbreakable(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() > 0.0F) {
            return; // 正常可破坏方块：不干预
        }
        // 不可破坏方块（基岩/屏障/命令方块等）：技能开启 + 持镐 → 按黑曜石速度挖掘
        if (org.zifeng.skilltree.event.UltimateEvents.canBreakUnbreakable(player)) {
            cir.setReturnValue(net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()
                    .getDestroyProgress(player, level, pos));
        }
    }
}
