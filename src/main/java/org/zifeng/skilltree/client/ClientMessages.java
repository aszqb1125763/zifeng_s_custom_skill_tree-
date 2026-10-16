package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端/服务端统一的「给玩家显示一条消息」入口（★ 26.1.2 移植新增）。
 *
 * <p><b>为什么需要它</b>：旧版所有代码都调 {@code Player#displayClientMessage(Component, boolean)}
 * —— 该方法在 26.1.2 <b>已被删除</b>（全代码库 0 引用）。替代品只有服务端一侧：
 * <ul>
 *   <li>服务端：{@link ServerPlayer#sendSystemMessage(Component, boolean)}（第二参数 = 动作栏）
 *       —— 与旧的 {@code displayClientMessage} 签名语义完全一致；</li>
 *   <li>客户端：{@code LocalPlayer} 只有单参 {@code sendSystemMessage(Component)}（进聊天栏），
 *       动作栏要用 {@code Gui#setOverlayMessage(Component, boolean animate)}。</li>
 * </ul>
 *
 * <p>本类的存在只是把这三种情况收在一处，避免 29 个调用点各自判断
 * 「当前是哪个逻辑端」——技能树里有大量按键/选区逻辑同时跑在两侧。
 */
public final class ClientMessages {

    private ClientMessages() {
    }

    /**
     * 显示一条消息。行为与 1.21.1 的 {@code displayClientMessage} 等价：
     * {@code actionBar = true} 走动作栏（不刷聊天栏），{@code false} 走聊天栏。
     */
    public static void display(Player player, Component message, boolean actionBar) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, actionBar);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (actionBar) {
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(message, false);
            }
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }
}
