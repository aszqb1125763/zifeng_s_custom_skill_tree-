package org.zifeng.skilltree.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 万物挖掘（ULT_BREAK_ALL，2026-09-01 重写）：
 * 让基岩等原版不可破坏方块（destroySpeed == -1）可以被玩家挖掘。
 * <p>
 * ⚠️ 1.20.1 源码确认（BlockBehaviour.java:364）：
 * {@code getDestroyProgress(BlockState, Player, BlockGetter, BlockPos)F} 定义在
 * {@link BlockBehaviour}（Block 继承，不重声明）；{@code BlockStateBase} 的 3 参版本
 * （BlockBehaviour.java:640，srg m_60625_）只是委托它：
 * <pre>
 *   // BlockStateBase（3 参，客户端 continueDestroyBlock / 服务端 handleBlockBreakAction /
 *   // incrementDestroyProgress 全部调用它）
 *   public float getDestroyProgress(Player p, BlockGetter l, BlockPos pos) {
 *       return this.getBlock().getDestroyProgress(this.asState(), p, l, pos); // → 本 Mixin 注入点（4 参）
 *   }
 *   // BlockBehaviour（4 参，本 Mixin 注入点）
 *   public float getDestroyProgress(BlockState s, Player p, BlockGetter l, BlockPos pos) {
 *       float f = s.getDestroySpeed(l, pos);
 *       if (f == -1.0F) return 0.0F;   // ← 基岩/屏障/命令方块 → 0，进度永不增长
 *       return p.getDigSpeed(s, pos) / f / (正确工具 ? 30 : 100);
 *   }
 * </pre>
 * 注入 4 参版本 = 客户端+服务端所有进度计算的最底层唯一入口（3 参必然委托它），
 * 比注入 3 参更可靠。require=1：注入失败直接启动报错，绝不静默失效。
 * <p>
 * 效果：返回值 ≤ 0（不可破坏方块）+ 技能开启 + 持镐 → 按黑曜石速度×4 挖掘
 * （钻石镐约 2.4 秒挖穿，原版黑曜石 9.4 秒太慢）。挖掘进度放行后，破坏完成
 * 由 {@link ServerPlayerGameModeMixin}（canHarvestBlock 调用点）放行掉落生成，
 * 再由 SkillTreeLootModifier（GLM）补基岩/屏障掉落。
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockStateMixin {

    @Inject(method = "getDestroyProgress",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$breakUnbreakable(BlockState state, Player player, BlockGetter level, BlockPos pos,
                                         CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() > 0.0F) {
            return; // 正常可破坏方块：不干预
        }
        // 不可破坏方块（基岩/屏障/命令方块等，destroySpeed == -1 → 返回 0）
        if (org.zifeng.skilltree.event.UltimateEvents.canBreakUnbreakable(player)) {
            // 按黑曜石速度 ×4 挖掘（钻石镐 ≈ 2.4 秒；clamp 到 1.0 防瞬挖溢出）
            float obsidian = Blocks.OBSIDIAN.defaultBlockState()
                    .getDestroyProgress(player, level, pos);
            cir.setReturnValue(Math.min(obsidian * 4.0F, 1.0F));
        }
    }
}
