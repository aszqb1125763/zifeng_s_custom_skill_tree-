package org.zifeng.skilltree.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.zifeng.skilltree.client.renderer.AuraSwordRenderer;
import org.zifeng.skilltree.client.screen.StarEnergyConverterScreen;
import org.zifeng.skilltree.init.ModEntities;

/**
 * 客户端 MOD 总线事件手动注册（NeoForge 1.21 推荐方式，避免 @EventBusSubscriber 扫描顺序问题）。
 */
public class ClientRegistrar {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModKeyBindings::registerKeyBindings);
        modEventBus.addListener(StarEnergyConverterScreen::registerScreens);
        modEventBus.addListener(ClientRegistrar::registerEntityRenderers);
        // GAME 总线客户端事件手动注册（确保触发）
        NeoForge.EVENT_BUS.register(ModKeyBindingEvents.class);
    }

    /** 注册钻石剑渲染器 */
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AURA_SWORD.get(), AuraSwordRenderer::new);
    }
}
