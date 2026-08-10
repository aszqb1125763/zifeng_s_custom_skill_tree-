package org.zifeng.skilltree.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.zifeng.skilltree.client.screen.SkillPointConverterScreen;

/**
 * 客户端 MOD 总线事件手动注册（NeoForge 1.21 推荐方式，避免 @EventBusSubscriber 扫描顺序问题）。
 */
public class ClientRegistrar {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModKeyBindings::registerKeyBindings);
        modEventBus.addListener(SkillPointConverterScreen::registerScreens);
        // GAME 总线客户端事件手动注册（确保触发）
        NeoForge.EVENT_BUS.register(ModKeyBindingEvents.class);
        // 凤凰涅槃冷却 HUD 提示（RenderGuiEvent.Post）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ReviveHudRenderer.class);
    }
}
