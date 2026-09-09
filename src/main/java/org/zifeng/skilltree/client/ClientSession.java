package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 客户端会话层（2026-09-09 v1-1，三级系统第二级"玩家子系统"骨架）。
 * <p>职责三件：认链接 / 给时间 / 做回收——全部事件驱动，零每 tick 轮询。
 * <ul>
 *   <li><b>认链接</b>：本地玩家实体变更（进服 / 死亡重生 / 换维度 = 新 LocalPlayer 加入世界）
 *       视为一次"钥匙链接（重新）建立"；退出服务器视为"链接断开"。用事件报告识别，不每 tick 比对。</li>
 *   <li><b>给时间</b>：{@link #now()} 提供统一"世界时钟"（客户端世界游戏时间，单调递增）。
 *       死亡重生 / 换维度不归零 → 根治"旧身体 tickCount + 冷却"在复活后永久拦截输入类 bug
 *       （与 2026-09-07 服务端闪现同根修复：改用 level.getGameTime()）。</li>
 *   <li><b>做回收</b>：链接断开/重建时 {@link #resetSession()} 广播，各模块只清"本次会话的临时状态"
 *       （半截选区第一角、成区冷却、防抖计数），保留"数据档案"（已配置的区/绑定列表，等服务端数据自然刷新）。</li>
 * </ul>
 * 各模块如需参与回收：提供 {@code static void resetSession()} 并在本类 resetSession() 中登记调用。
 */
public final class ClientSession {
    private ClientSession() {
    }

    /** 当前会话的本地玩家（链接对象）；null = 未进服/已断开 */
    private static LocalPlayer currentPlayer = null;

    /**
     * 统一世界时钟：客户端世界游戏时间（tick 数，long 单调递增）。
     * 死亡重生/换维度不归零；换服务器会归零——由 {@link #onLoggingOut} 重置兜底。
     */
    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    /** 本地玩家加入世界：进服 / 死亡重生 / 换维度 = 会话（重新）建立 */
    @SubscribeEvent
    public static void onPlayerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (player == currentPlayer) {
            return; // 同一实体重复加入（非会话变更）：跳过
        }
        currentPlayer = player;
        resetSession();
    }

    /** 退出服务器/切换服务器：链接断开，清会话 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        currentPlayer = null;
        resetSession();
    }

    /** 会话重置广播：只清"临时会话态"，保留"数据档案"。各模块在下方登记自己的 resetSession。 */
    public static void resetSession() {
        MagnetExclusionInputHandler.resetSession();
        ZoneSelectionInputHandler.resetSession();
    }
}
