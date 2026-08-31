package org.zifeng.skilltree.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 万物挖掘（ULT_BREAK_ALL）：修复"有进度条但无掉落"（2026-09-01 最终方案）。
 * <p>
 * ⚠️ 为什么不能直接 Mixin canHarvestBlock（三次尝试的教训）：
 * <ul>
 *   <li>{@code BlockState} / {@code BlockStateBase} 类字节码里<b>没有</b> canHarvestBlock 方法体——
 *       它是 Forge 运行时接口注入的 default 方法（IForgeBlockState），Mixin 到类上找不到注入点
 *       （require=0 静默失效 → 掉落仍无）；Mixin 到接口 IForgeBlockState 会报
 *       {@code @Mixin target type mismatch} 启动崩溃（class 形式）/ {@code Injector in interface
 *       is unsupported}（interface 形式，Mixin 0.8.5 限制）。</li>
 * </ul>
 * 两个 @Redirect（均在 destroyBlock 方法内，不同调用点）：
 * <ol>
 *   <li>canHarvestBlock 调用点（字节码 invokevirtual BlockState.canHarvestBlock）：
 *       技能开启 + 持镐 → true → 原版进入掉落生成（playerDestroy → spawnAfterBreak）→
 *       BlockDropsMixin（getDrops）补基岩/屏障掉落。
 *       原逻辑等价于 {@code ForgeHooks.isCorrectToolForDrops(state, player)}
 *       （IForgeBlock.canHarvestBlock 的 default 实现），此处直接调用等价实现。</li>
 *   <li>{@code player.canUseGameMasterBlocks()} 调用点（字节码 invokevirtual ServerPlayer.m_36337_）：
 *       命令方块/结构方块/拼图方块（GameMasterBlock）原版只有创造（abilities.instabuild=true）
 *       能破坏（否则 destroyBlock 直接 return false → 客户端进度条被服务端 BlockUpdate 中断重置）。
 *       技能开启 + 持镐 → 同样放行（与创造同机制），随后掉落由 BlockDropsMixin 补。</li>
 * </ol>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Redirect(method = "destroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;canHarvestBlock"
                            + "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/entity/player/Player;)Z"),
            require = 1)
    private boolean zifeng$allowHarvestUnbreakable(BlockState state, BlockGetter level, BlockPos pos,
                                                   Player player) {
        if (net.minecraftforge.common.ForgeHooks.isCorrectToolForDrops(state, player)) {
            return true; // 原本就能采集：保持原行为
        }
        // 原版不可采集（基岩等无法正常挖掘）：技能开启 + 持镐 → 允许掉落生成
        return org.zifeng.skilltree.event.UltimateEvents.canBreakUnbreakable(player);
    }

    /**
     * GameMasterBlock（命令方块/结构方块/拼图方块）放行——与创造模式同机制：
     * 原版 {@code destroyBlock} 里 {@code block instanceof GameMasterBlock && !canUseGameMasterBlocks()}
     * → return false（进度条中断）。创造模式靠 canUseGameMasterBlocks()（= instabuild）为 true 通过；
     * 这里让技能开启 + 持镐的玩家同样通过。
     */
    @Redirect(method = "destroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;canUseGameMasterBlocks()Z"),
            require = 1)
    private boolean zifeng$allowGameMasterBlock(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true; // 原版：创造模式
        }
        // 万物挖掘：命令方块/结构方块/拼图方块等同创造可破坏
        return org.zifeng.skilltree.event.UltimateEvents.canBreakUnbreakable(player);
    }
}
